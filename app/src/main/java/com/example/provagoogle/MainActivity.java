package com.example.provagoogle;

import android.os.Bundle;

// import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
// import androidx.core.graphics.Insets;
// import androidx.core.view.ViewCompat;
// import androidx.core.view.WindowInsetsCompat;
import android.content.Intent;
// import android.os.Bundle;
import android.widget.Toast;

// import androidx.activity.result.ActivityResultLauncher;
// import androidx.activity.result.contract.ActivityResultContracts;
// import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
// import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

// import java.io.IOException;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {

    // @Override
    private static final int RC_SIGN_IN = 9001;
    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicialitzar Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope("https://www.googleapis.com/auth/drive.file"))  // permís mínim recomanat
                //.requestScopes(new Scope(DriveScopes.DRIVE))  // si necessites accés complet
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Exemple: botó per iniciar sessió i pujar
        findViewById(R.id.btnUpload).setOnClickListener(v -> signInAndUpload());
    }

    private void signInAndUpload() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            com.google.android.gms.tasks.Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                uploadFileToDrive(account);
            } catch (ApiException e) {
                Toast.makeText(this, "Error sign-in: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void uploadFileToDrive(GoogleSignInAccount account) {
        new Thread(() -> {
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        MainActivity.this,
                        Collections.singleton("https://www.googleapis.com/auth/drive.file")
                );
                credential.setSelectedAccount(account.getAccount());

                Drive service = new Drive.Builder(
                        new NetHttpTransport(),
                        GsonFactory.getDefaultInstance(),
                        credential)
                        .setApplicationName("La teva App")
                        .build();

                // Exemple: fitxer local que vols pujar
                java.io.File filePath = new java.io.File("/storage/emulated/0/Download/exemple.jpg");

                File fileMetadata = new File();
                fileMetadata.setName("exemple_" + System.currentTimeMillis() + ".jpg");
                fileMetadata.setMimeType("image/jpeg");
                // fileMetadata.setParents(Collections.singletonList("tu_folder_id_aqui"));  // opcional

                FileContent mediaContent = new FileContent("image/jpeg", filePath);

                File uploadedFile = service.files().create(fileMetadata, mediaContent)
                        .setFields("id, name, webViewLink")
                        .execute();

                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this,
                                "Fitxer pujat! ID: " + uploadedFile.getId(),
                                Toast.LENGTH_LONG).show()
                );

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this,
                                "Error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }
}