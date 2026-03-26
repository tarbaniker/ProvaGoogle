package com.example.provagoogle;

import android.accounts.Account;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.services.AbstractGoogleClient;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CredentialManager";
    private CredentialManager credentialManager;
    private final Executor executor = Executors.newSingleThreadExecutor();

    // Variable per guardar l'email i reintentar la pujada després de l'autorització
    private String currentEmail;

    // Launcher per gestionar la finestra d'autorització de Google
    private final ActivityResultLauncher<Intent> driveAuthLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            Log.i(TAG, "Autorització concedida. Reintentant pujada...");
                            if (currentEmail != null) {
                                executor.execute(() -> uploadFileToDrive(currentEmail));
                            }
                        } else {
                            Log.e(TAG, "L'usuari ha denegat l'autorització.");
                            Toast.makeText(this, "Cal autoritzar l'accés a Drive per continuar", Toast.LENGTH_SHORT).show();
                        }
                    });

    private static final String WEB_CLIENT_ID = "874628293746-n19a7qm9c5821k15kfchd26s0rdcjp8s.apps.googleusercontent.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        credentialManager = CredentialManager.create(this);

        Button btnSignIn = findViewById(R.id.btnSignIn);
        btnSignIn.setOnClickListener(v -> signInWithGoogle());
    }

    private void signInWithGoogle() {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                this,
                request,
                null,
                executor,
                new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        handleSignInResponse(response);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        Log.e(TAG, "Sign-in error: " + e.getClass().getName(), e);
                        runOnUiThread(() -> {
                            String errorMsg = e.getMessage();
                            if (e instanceof NoCredentialException) {
                                errorMsg = "No s'han trobat comptes.";
                            }
                            Toast.makeText(MainActivity.this, "Error: " + errorMsg, Toast.LENGTH_LONG).show();
                        });
                    }
                }
        );
    }

    private void handleSignInResponse(GetCredentialResponse response) {
        Credential credential = response.getCredential();
        if (credential instanceof CustomCredential) {
            CustomCredential customCredential = (CustomCredential) credential;
            if (GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())) {
                GoogleIdTokenCredential googleIdTokenCredential =
                        GoogleIdTokenCredential.createFrom(customCredential.getData());

                String email = googleIdTokenCredential.getId();
                if (email == null || !email.contains("@")) {
                    try {
                        GoogleIdToken parsedToken = GoogleIdToken.parse(GsonFactory.getDefaultInstance(), googleIdTokenCredential.getIdToken());
                        email = parsedToken.getPayload().getEmail();
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsejant el JWT", e);
                    }
                }

                if (email != null && !email.isEmpty()) {
                    final String finalEmail = email;
                    runOnUiThread(() -> Toast.makeText(this, "Sessió iniciada: " + finalEmail, Toast.LENGTH_SHORT).show());
                    uploadFileToDrive(finalEmail);
                }
            }
        }
    }

    private void uploadFileToDrive(String email) {
        try {
            Log.i(TAG, "Iniciant pujada a Drive per a: " + email);

            GoogleAccountCredential driveCredential = GoogleAccountCredential.usingOAuth2(
                    this, Collections.singletonList(DriveScopes.DRIVE));
            driveCredential.setSelectedAccount(new Account(email, "com.google"));

            Drive service = new Drive.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    driveCredential)
                    .setApplicationName("ProvaGoogle")
                    .build();

            // Preparar fitxer temporal
            java.io.File tempFile = new java.io.File(getCacheDir(), "upload.txt");
            try (InputStream is = getResources().openRawResource(R.raw.provesgoogledrive);
                 FileOutputStream os = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }

            String folderId = "1UJ6V3TXhzsurtQJEgNx28dJ0PF3jtPOi";  // ID de la carpeta Snooker

            File fileMetadata = new File();
            fileMetadata.setName("upload.txt");
            fileMetadata.setMimeType("text/plain");
            FileContent mediaContent = new FileContent("text/plain", tempFile);
            fileMetadata.setParents(Collections.singletonList(folderId)); //Indiquem la carpeta destí

            try {
                File uploadedFile = service.files().create(fileMetadata, mediaContent)
                        .setFields("id, parents")
                        .execute();

                if (uploadedFile != null) {
                    Log.i(TAG, "Fitxer pujat! ID: " + uploadedFile.getId() + ", Parents: " + uploadedFile.getParents());
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Fitxer pujat amb èxit!", Toast.LENGTH_LONG).show());
                }
            } catch (UserRecoverableAuthIOException e) {
                // Captura l'error d'autorització i llança el flux per demanar permisos a l'usuari
                Log.w(TAG, "UserRecoverableAuthIOException: Cal autorització de Drive.");
                currentEmail = email;
                runOnUiThread(() -> driveAuthLauncher.launch(e.getIntent()));
            } catch (Exception e) {
                Log.e(TAG, "Error Drive (execute): " + e.getMessage(), e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error Drive (setup): " + e.getMessage(), e);
        }
    }
}
