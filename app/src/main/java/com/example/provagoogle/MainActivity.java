package com.example.provagoogle;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import androidx.credentials.Credential;
import androidx.credentials.CustomCredential;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CredentialManager";
    private CredentialManager credentialManager;
    private final Executor executor = Executors.newSingleThreadExecutor();

    // Canvia-ho pel teu **Web application** Client ID del Google Cloud Console
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
//                .setFilterByAuthorizedAccounts(false)
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false) // Desactivat per forçar el selector si hi ha algun error de 'matching'
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
                                errorMsg = "No s'han trobat comptes. Revisa el Web Client ID i la configuració a Google Cloud Console (SHA-1).";
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

                String idToken = googleIdTokenCredential.getIdToken();
                String email = googleIdTokenCredential.getId();           // normalment l'email
                String displayName = googleIdTokenCredential.getDisplayName();

                runOnUiThread(() -> {
                    Toast.makeText(this,
                            "Sign in correcte!\nEmail: " + email,
                            Toast.LENGTH_LONG).show();
                });

                Log.d("SignIn", "ID Token: " + idToken);
                // Ara pots utilitzar l'idToken (per exemple, enviar al teu backend)
                // o demanar autorització per Drive

            } else {
                // Altres tipus de CustomCredential (passkeys, etc.)
                runOnUiThread(() ->
                        Toast.makeText(this, "Tipus de credential inesperat: " + customCredential.getType(),
                                Toast.LENGTH_SHORT).show()
                );
            }
        } else {
            runOnUiThread(() ->
                    Toast.makeText(this, "Tipus de credential no esperat", Toast.LENGTH_SHORT).show()
            );
        }
    }


}
