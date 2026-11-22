package sv.edu.utec.app;

import sv.edu.utec.datos.ConexionFirestore;
import sv.edu.utec.datos.FirestoreProductoRepositorio;
import sv.edu.utec.datos.ProductoRepositorio;
import sv.edu.utec.datos.RepositorioException;
import sv.edu.utec.modelo.Producto;

import java.util.List;

public class PruebaFirestore {

    public static void main(String[] args) {
        try {
            ProductoRepositorio repo =
                    new FirestoreProductoRepositorio(ConexionFirestore.getFirestore());

            // PARTE C: CREATE, DELETE y buscar uno
            for (Producto p : DatosDemo.productos()) {
                repo.eliminar(p.getCodigo());
            }
            repo.eliminar("P-009");
            repo.eliminar("P-011");
            System.out.println("0) Limpieza lista");

            int n = repo.insertarVarios(DatosDemo.productos());
            System.out.println("1) Carga inicial: " + n + " productos");

            repo.insertar(new Producto("P-009", "Azucar blanca 1 kg",
                    "Abarrotes", 1.10, 150));
            System.out.println("   Insertado: P-009");

            try {
                repo.insertar(new Producto("P-001", "Duplicado", "Abarrotes", 1.00, 1));
            } catch (RepositorioException e) {
                System.out.println("   Rechazado: " + e.getMessage());
            }

            try {
                repo.insertarVarios(List.of(
                        new Producto("P-011", "Sal de mesa 1 kg", "Abarrotes", 0.85, 200),
                        new Producto("P-002", "Duplicado", "Abarrotes", 1.00, 1)));
            } catch (RepositorioException e) {
                System.out.println("   Lote rechazado: " + e.getMessage());
            }
            System.out.println("   P-011 existe despues del lote: "
                    + repo.buscarPorCodigo("P-011").isPresent());

            try {
                repo.buscarPorCodigo("P-001/ventas/V-1");
            } catch (IllegalArgumentException e) {
                System.out.println("   Rechazado por Java: " + e.getMessage());
            }

            System.out.println("2) P-009 eliminado: " + repo.eliminar("P-009"));
            System.out.println("   P-009 eliminado otra vez: " + repo.eliminar("P-009"));

            // Aqui agregara los bloques de las partes D y E
            // PARTE D: Consultas y filtros
            System.out.println("\n3) Todos los productos (" + repo.listarTodos().size() + "):");
            repo.listarTodos().forEach(p -> System.out.println("   " + p));

            System.out.println("\n4) Buscar por nombre 'arroz':");
            repo.buscarPorNombre("arroz").forEach(p -> System.out.println("   " + p));

            System.out.println("\n5) Categoria 'Bebidas':");
            repo.listarPorCategoria("Bebidas").forEach(p -> System.out.println("   " + p));

            System.out.println("\n6) Precio entre $1.00 y $3.00:");
            repo.buscarPorRangoDePrecio(1.00, 3.00).forEach(p -> System.out.println("   " + p));

            // PARTE E: Actualizaciones y resumen
            Producto p001 = repo.buscarPorCodigo("P-001").orElseThrow();
            p001.setPrecio(p001.getPrecio() + 0.25);
            System.out.println("\n7) P-001 actualizado: " + repo.actualizar(p001));
            System.out.println("   Nuevo valor: " + repo.buscarPorCodigo("P-001").orElseThrow());

            System.out.println("\n8) Ajuste existencia P-001 (+10): "
                    + repo.ajustarExistencia("P-001", 10));
            try {
                repo.ajustarExistencia("P-001", -99999);
            } catch (RepositorioException e) {
                System.out.println("   Stock negativo rechazado: " + e.getMessage());
            }

            System.out.println("\n9) Resumen por categoria:");
            repo.resumenPorCategoria().forEach(r -> System.out.println("   " + r));

        } catch (RepositorioException | IllegalStateException e) {
            System.err.println("Error de datos: " + e.getMessage());
        } finally {
            ConexionFirestore.cerrar();
        }
    }
}