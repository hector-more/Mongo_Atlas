package sv.edu.utec.datos;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConexionFirestore {

    private static Firestore db;

    private ConexionFirestore() { }

    public static synchronized Firestore getFirestore() {
        if (db == null) {
            String ruta = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (ruta == null || ruta.isBlank()) {
                throw new IllegalStateException(
                        "Falta la variable de entorno GOOGLE_APPLICATION_CREDENTIALS"
                                + " (Run > Edit Configurations).");
            }
            if (!Files.isRegularFile(Path.of(ruta))) {
                throw new IllegalStateException(
                        "No existe el archivo de la cuenta de servicio: " + ruta);
            }
            try {
                FirebaseOptions opciones = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .build();
                FirebaseApp.initializeApp(opciones);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "No se pudo leer la cuenta de servicio: " + e.getMessage(), e);
            }
            db = FirestoreClient.getFirestore();
        }
        return db;
    }

    public static synchronized void cerrar() {
        if (db != null) {
            FirebaseApp.getInstance().delete();
            db = null;
        }
    }
}