package sv.edu.utec.app;

import com.mongodb.MongoException;
import sv.edu.utec.datos.FabricaRepositorio;
import sv.edu.utec.datos.FabricaRepositorio.Motor;
import sv.edu.utec.datos.ProductoRepositorio;
import sv.edu.utec.datos.RepositorioException;

import java.util.Locale;
import java.util.Scanner;

/**
 * Punto de arranque: aquí, y solo aquí, se decide qué motor de base de datos
 * se utiliza (MongoDB o Cloud Firestore).
 */
public class App {

    public static void main(String[] args) {
        Motor motor = null;
        try (Scanner entrada = new Scanner(System.in)) {
            motor = seleccionarMotor(args, entrada);
            ProductoRepositorio repo = FabricaRepositorio.crear(motor);

            if (repo.listarTodos().isEmpty()) {
                int n = repo.insertarVarios(DatosDemo.productos());
                System.out.println("Coleccion vacia: se cargaron " + n + " productos de ejemplo.");
            }

            new MenuInventario(repo, entrada).iniciar();

        } catch (MongoException e) {
            System.err.println("Error de MongoDB: " + e.getMessage());
        } catch (RepositorioException | IllegalStateException | IllegalArgumentException e) {
            System.err.println("Error de base de datos: " + e.getMessage());
        } finally {
            if (motor != null) {
                FabricaRepositorio.cerrar(motor);
            }
        }
    }

    private static Motor seleccionarMotor(String[] args, Scanner entrada) {
        String valor = (args.length > 0 && !args[0].isBlank())
                ? args[0]
                : System.getenv("MOTOR_BD");

        if (valor != null && !valor.isBlank()) {
            return switch (valor.trim().toUpperCase(Locale.ROOT)) {
                case "FIRESTORE", "FS", "2" -> Motor.FIRESTORE;
                case "MONGODB", "MONGO", "1" -> Motor.MONGODB;
                default -> throw new IllegalArgumentException(
                        "Motor no reconocido: " + valor + " (use MONGODB o FIRESTORE)");
            };
        }

        System.out.println("=== Seleccione el motor de base de datos ===");
        System.out.println("1. MongoDB Atlas");
        System.out.println("2. Google Cloud Firestore");
        System.out.print("Opcion [2]: ");
        String opcion = entrada.nextLine().trim();
        return "1".equals(opcion) ? Motor.MONGODB : Motor.FIRESTORE;
    }
}