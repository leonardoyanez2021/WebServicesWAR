package es.altia.bne.test.cron;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.junit.Before;
import org.junit.Test;

import es.altia.bne.cron.jobs.CargaFicherosAFCJob;

/**
 * Test standalone del job CargaFicherosAFC sin Spring Útil para debuggear la lógica básica del job
 */
public class TestAFCStandalone {

    private String directorioArchivos;

    @Before
    public void setUp() {
        System.out.println("=== Test STANDALONE del job CargaFicherosAFC ===");
        System.out.println("NOTA: Este test ejecuta solo la lógica básica del job");
        System.out.println("No se conecta a BD ni carga servicios Spring");
        System.out.println();

        // Usar directorio dentro del proyecto
        this.directorioArchivos = "D:/BNEenv/wks-develop/ISSUE_887801/test-data/afc";

        final File dir = new File(this.directorioArchivos);
        dir.mkdirs();

        System.out.println("Directorio: " + this.directorioArchivos);

        // Crear archivos de prueba con fecha de ayer
        final Calendar ayer = Calendar.getInstance();
        ayer.add(Calendar.DAY_OF_YEAR, -1);
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        final String fechaAyer = sdf.format(ayer.getTime());

        System.out.println("Fecha de ayer: " + fechaAyer);

        try {
            this.crearArchivoSiNoExiste(this.directorioArchivos + "/AFC_GIROS_" + fechaAyer + ".txt", "RUT;NOMBRE;MONTO;FECHA;ESTADO\n"
                    + "12345678-9;Juan Perez;150000;" + fechaAyer + ";A\n" + "98765432-1;Maria Lopez;200000;" + fechaAyer + ";A");

            this.crearArchivoSiNoExiste(this.directorioArchivos + "/AFC_GIROS_EXTRA_" + fechaAyer + ".txt",
                    "RUT;NOMBRE;MONTO_EXTRA;FECHA;TIPO\n" + "11111111-1;Pedro Silva;50000;" + fechaAyer + ";EXTRA\n"
                            + "22222222-2;Ana Gomez;75000;" + fechaAyer + ";EXTRA");

            this.crearArchivoSiNoExiste(this.directorioArchivos + "/AFC_PAGOS_" + fechaAyer + ".txt",
                    "RUT;NOMBRE;MONTO_PAGO;FECHA;ESTADO_PAGO\n" + "33333333-3;Luis Torres;100000;" + fechaAyer + ";PAGADO\n"
                            + "44444444-4;Carmen Ruiz;120000;" + fechaAyer + ";PAGADO");

            System.out.println("✓ Archivos de prueba listos");

        } catch (final Exception e) {
            System.err.println("Error creando archivos: " + e.getMessage());
        }

        System.out.println("===========================================");
    }

    /**
     * Test básico - instanciar el job sin Spring
     */
    @Test
    public void testInstanciarJob() {
        System.out.println("Test 1: Instanciación básica del job");

        try {
            final CargaFicherosAFCJob job = new CargaFicherosAFCJob();
            System.out.println("✓ Job instanciado correctamente: " + job.getClass().getName());

        } catch (final Exception e) {
            System.err.println("✗ Error instanciando job: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test para revisar qué archivos busca el job
     */
    @Test
    public void testLogicaArchivos() {
        System.out.println("Test 2: Lógica de búsqueda de archivos");

        // Simular lo que hace el job para buscar archivos
        final Calendar ayer = Calendar.getInstance();
        ayer.add(Calendar.DAY_OF_YEAR, -1);

        final String formatoGiros = "AFC_GIROS_%tY%tm%td.txt";
        final String formatoGirosExtra = "AFC_GIROS_EXTRA_%tY%tm%td.txt";
        final String formatoPagos = "AFC_PAGOS_%tY%tm%td.txt";

        final String archivoGiros = String.format(formatoGiros, ayer, ayer, ayer);
        final String archivoGirosExtra = String.format(formatoGirosExtra, ayer, ayer, ayer);
        final String archivoPagos = String.format(formatoPagos, ayer, ayer, ayer);

        System.out.println("Archivos que debería buscar el job:");
        System.out.println("- " + archivoGiros);
        System.out.println("- " + archivoGirosExtra);
        System.out.println("- " + archivoPagos);

        System.out.println("\nVerificando existencia:");
        final File fGiros = new File(this.directorioArchivos + "/" + archivoGiros);
        final File fGirosExtra = new File(this.directorioArchivos + "/" + archivoGirosExtra);
        final File fPagos = new File(this.directorioArchivos + "/" + archivoPagos);

        System.out.println("- " + archivoGiros + ": " + (fGiros.exists() ? "✓ Existe" : "✗ No existe"));
        System.out.println("- " + archivoGirosExtra + ": " + (fGirosExtra.exists() ? "✓ Existe" : "✗ No existe"));
        System.out.println("- " + archivoPagos + ": " + (fPagos.exists() ? "✓ Existe" : "✗ No existe"));
    }

    /**
     * Test de ejecución limitada (sin servicios)
     */
    @Test
    public void testEjecucionLimitada() {
        System.out.println("Test 3: Intento de ejecución (esperamos errores de dependencias)");

        try {
            final CargaFicherosAFCJob job = new CargaFicherosAFCJob();

            System.out.println("Intentando ejecutar job...");
            System.out.println("NOTA: Es normal que falle por dependencias no inyectadas");

            job.execute(null);

            System.out.println("✓ Job ejecutado sin errores (inesperado)");

        } catch (final NullPointerException e) {
            System.out.println("✓ Error esperado por dependencias: " + e.getMessage());
            System.out.println("  Esto confirma que el job intenta usar servicios Spring");

        } catch (final Exception e) {
            System.out.println("✗ Error inesperado: " + e.getClass().getSimpleName() + " - " + e.getMessage());

            // Mostrar solo las primeras líneas del stack trace
            final StackTraceElement[] stack = e.getStackTrace();
            for (int i = 0; i < Math.min(3, stack.length); i++) {
                System.out.println("    " + stack[i]);
            }
        }
    }

    /**
     * Test para revisar propiedades necesarias
     */
    @Test
    public void testConfiguracionNecesaria() {
        System.out.println("Test 4: Configuración necesaria");

        System.out.println("Para que el job funcione completamente necesitas:");
        System.out.println("1. Agregar a jobs.properties:");
        System.out.println("   bne.integraciones.afc.ftp.localdir=" + this.directorioArchivos);
        System.out.println("   bne.integraciones.afc.ftp.remotedir=" + this.directorioArchivos);
        System.out.println("   #bne.integraciones.afc.ftp.host= (comentado para modo local)");
        System.out.println();
        System.out.println("2. Servicios Spring requeridos:");
        System.out.println("   - IAFCService afcService");
        System.out.println("   - IEstadoPerfilPostulanteService estadoPerfilPostulanteService");
        System.out.println("   - Otros servicios de negocio...");
        System.out.println();
        System.out.println("3. Configuración de base de datos funcionando");
    }

    private void crearArchivoSiNoExiste(final String ruta, final String contenido) {
        try {
            final File archivo = new File(ruta);
            if (!archivo.exists()) {
                java.nio.file.Files.write(archivo.toPath(), contenido.getBytes());
                System.out.println("✓ Creado: " + archivo.getName());
            } else {
                System.out.println("✓ Existe: " + archivo.getName());
            }
        } catch (final Exception e) {
            System.err.println("Error creando " + ruta + ": " + e.getMessage());
        }
    }
}