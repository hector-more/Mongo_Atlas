package sv.edu.utec.datos;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import sv.edu.utec.modelo.Producto;
import sv.edu.utec.modelo.ResumenCategoria;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class FirestoreProductoRepositorio implements ProductoRepositorio {

    private final Firestore db;
    private final CollectionReference coleccion;

    public FirestoreProductoRepositorio(Firestore db) {
        this.db = db;
        this.coleccion = db.collection("productos");
    }

    // --- PARTE C ---

    @Override
    public void insertar(Producto p) {
        DocumentReference ref = referencia(p.getCodigo());
        boolean creado = esperar(db.runTransaction(tx -> {
            if (tx.get(ref).get().exists()) {
                return false;
            }
            tx.create(ref, aMapa(p));
            return true;
        }), "insertar");

        if (!creado) {
            throw new RepositorioException(
                    "Ya existe un producto con el codigo " + p.getCodigo());
        }
    }

    @Override
    public int insertarVarios(List<Producto> productos) {
        if (productos.isEmpty()) {
            return 0;
        }
        DocumentReference[] refs = new DocumentReference[productos.size()];
        for (int i = 0; i < refs.length; i++) {
            refs[i] = referencia(productos.get(i).getCodigo());
        }
        String repetido = esperar(db.runTransaction(tx -> {
            for (DocumentSnapshot d : tx.getAll(refs).get()) {
                if (d.exists()) {
                    return d.getId();
                }
            }
            for (int i = 0; i < refs.length; i++) {
                tx.create(refs[i], aMapa(productos.get(i)));
            }
            return null;
        }), "insertar el lote");

        if (repetido != null) {
            throw new RepositorioException("Carga cancelada: el codigo "
                    + repetido + " ya existe; no se inserto ningun producto");
        }
        return productos.size();
    }

    @Override
    public boolean eliminar(String codigo) {
        DocumentReference ref = referencia(codigo);
        return esperar(db.runTransaction(tx -> {
            if (!tx.get(ref).get().exists()) {
                return false;
            }
            tx.delete(ref);
            return true;
        }), "eliminar");
    }

    @Override
    public Optional<Producto> buscarPorCodigo(String codigo) {
        DocumentSnapshot d = esperar(referencia(codigo).get(), "buscar " + codigo);
        return d.exists() ? Optional.of(aProducto(d)) : Optional.empty();
    }

    // --- PARTES D y E (pendientes) ---

    // --- PARTE D: Consultas ---

    @Override
    public List<Producto> listarTodos() {
        List<QueryDocumentSnapshot> docs = esperar(
                coleccion.orderBy("codigo").get(), "listar todos").getDocuments();
        return aLista(docs);
    }

    @Override
    public List<Producto> buscarPorNombre(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        String filtro = texto.trim().toLowerCase(Locale.ROOT);
        return listarTodos().stream()
                .filter(p -> p.getNombre() != null
                        && p.getNombre().toLowerCase(Locale.ROOT).contains(filtro))
                .collect(Collectors.toList());
    }

    @Override
    public List<Producto> listarPorCategoria(String categoria) {
        Query consulta = coleccion.whereEqualTo("categoria", categoria);
        List<QueryDocumentSnapshot> docs = esperar(
                consulta.get(), "listar por categoria").getDocuments();
        List<Producto> resultado = aLista(docs);
        resultado.sort(Comparator.comparing(Producto::getNombre,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return resultado;
    }

    @Override
    public List<Producto> buscarPorRangoDePrecio(double minimo, double maximo) {
        Query consulta = coleccion
                .whereGreaterThanOrEqualTo("precio", minimo)
                .whereLessThanOrEqualTo("precio", maximo)
                .orderBy("precio", Query.Direction.ASCENDING);
        List<QueryDocumentSnapshot> docs = esperar(
                consulta.get(), "buscar por rango de precio").getDocuments();
        return aLista(docs);
    }

    // --- PARTE E: Actualizaciones y Resumen ---

    @Override
    public boolean actualizar(Producto p) {
        DocumentReference ref = referencia(p.getCodigo());
        return esperar(db.runTransaction(tx -> {
            if (!tx.get(ref).get().exists()) {
                return false;
            }
            Map<String, Object> cambios = new HashMap<>();
            cambios.put("nombre", p.getNombre());
            cambios.put("categoria", p.getCategoria());
            cambios.put("precio", p.getPrecio());
            cambios.put("existencia", p.getExistencia());
            cambios.put("actualizadoEn", FieldValue.serverTimestamp());
            tx.update(ref, cambios);
            return true;
        }), "actualizar");
    }

    @Override
    public boolean ajustarExistencia(String codigo, int cantidad) {
        DocumentReference ref = referencia(codigo);
        return esperar(db.runTransaction(tx -> {
            DocumentSnapshot d = tx.get(ref).get();
            if (!d.exists()) {
                return false;
            }
            long actual = d.getLong("existencia");
            long nueva = actual + cantidad;
            if (nueva < 0) {
                throw new RepositorioException(
                        "Existencia insuficiente para " + codigo
                                + " (actual: " + actual + ", ajuste: " + cantidad + ")");
            }
            tx.update(ref,
                    "existencia", nueva,
                    "actualizadoEn", FieldValue.serverTimestamp());
            return true;
        }), "ajustar existencia");
    }

    @Override
    public List<ResumenCategoria> resumenPorCategoria() {
        List<Producto> todos = listarTodos();
        Map<String, List<Producto>> porCategoria = todos.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCategoria() != null ? p.getCategoria() : "Sin categoria"));

        List<ResumenCategoria> resumen = new ArrayList<>();
        for (Map.Entry<String, List<Producto>> entry : porCategoria.entrySet()) {
            String cat = entry.getKey();
            List<Producto> items = entry.getValue();
            int cantidadProductos = items.size();
            int unidadesTotales = items.stream()
                    .mapToInt(Producto::getExistencia)
                    .sum();
            double valorInventario = items.stream()
                    .mapToDouble(p -> p.getPrecio() * p.getExistencia())
                    .sum();
            resumen.add(new ResumenCategoria(
                    cat, cantidadProductos, unidadesTotales, valorInventario));
        }
        resumen.sort(Comparator.comparing(ResumenCategoria::categoria));        return resumen;
    }

    private List<Producto> aLista(List<QueryDocumentSnapshot> docs) {
        List<Producto> lista = new ArrayList<>(docs.size());
        for (QueryDocumentSnapshot d : docs) {
            lista.add(aProducto(d));
        }
        return lista;
    }

    // --- Utilidades y traduccion objeto <-> documento ---

    private static <T> T esperar(ApiFuture<T> futuro, String operacion) {
        try {
            return futuro.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RepositorioException("Operacion interrumpida: " + operacion, e);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause();
            throw new RepositorioException(
                    "Firestore no pudo " + operacion + ": " + causa.getMessage(), causa);
        }
    }

    private DocumentReference referencia(String codigo) {
        if (codigo == null || codigo.isBlank() || codigo.contains("/")
                || codigo.equals(".") || codigo.equals("..")
                || codigo.matches("__.*__")) {
            throw new IllegalArgumentException("Codigo no valido para Firestore: " + codigo);
        }
        return coleccion.document(codigo);
    }

    private Map<String, Object> aMapa(Producto p) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", p.getCodigo());
        datos.put("nombre", p.getNombre());
        datos.put("categoria", p.getCategoria());
        datos.put("precio", p.getPrecio());
        datos.put("existencia", p.getExistencia());
        datos.put("creadoEn", FieldValue.serverTimestamp());
        return datos;
    }

    private Producto aProducto(DocumentSnapshot d) {
        return new Producto(
                d.getId(),
                d.getString("nombre"),
                d.getString("categoria"),
                d.getDouble("precio"),
                d.getLong("existencia").intValue());
    }
}