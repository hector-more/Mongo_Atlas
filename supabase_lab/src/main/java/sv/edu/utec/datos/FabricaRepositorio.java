package sv.edu.utec.datos;

public final class FabricaRepositorio {

    public enum Motor {
        MONGODB,
        FIRESTORE
    }

    private FabricaRepositorio() {
    }

    public static ProductoRepositorio crear(Motor motor) {
        return switch (motor) {
            case MONGODB -> new MongoProductoRepositorio(ConexionMongo.getBaseDeDatos());
            case FIRESTORE -> new FirestoreProductoRepositorio(ConexionFirestore.getFirestore());
        };
    }

    public static void cerrar(Motor motor) {
        switch (motor) {
            case MONGODB -> ConexionMongo.cerrar();
            case FIRESTORE -> ConexionFirestore.cerrar();
        }
    }
}