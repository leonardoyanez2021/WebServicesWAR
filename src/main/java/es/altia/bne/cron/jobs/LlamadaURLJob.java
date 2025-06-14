package es.altia.bne.cron.jobs;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.google.common.base.Throwables;

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaURLDto;
import es.altia.bne.model.entities.dto.auditoria.UrlsParaJobXmlDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;

@Scope("prototype")
@Component("jobURLs")
@DisallowConcurrentExecution
public class LlamadaURLJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${bne.integraciones.url.list}")
    private String listaUrls;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        final List<Integer> listaCodigosRespuestaHtpp = new ArrayList<>();
        final List<UrlsParaJobXmlDto> listaFinalTodo = new ArrayList<>();

        this.logger.info("Iniciando JOB de lectura de URL's para ");
        AuditoriaURLDto xmlAuditoria = null;

        try {
            // En el caso de meter mas de una URL se separan con $$ ya que el dato que llega es un String
            final String[] urlsSeparadas = this.listaUrls.split("\\$\\$");

            // Se recorre el Array para ver la URLS que hay, si esque hay mas de una, y se lanza el GET a cada una de ellas
            final HttpClient client = HttpClientBuilder.create().build();
            for (final String url : urlsSeparadas) {
                final HttpGet request = new HttpGet(url);

                try {
                    final HttpResponse response = client.execute(request);
                    final Integer respuestaHTTPCLient = response.getStatusLine().getStatusCode();
                    listaCodigosRespuestaHtpp.add(respuestaHTTPCLient);
                    listaFinalTodo.add(new UrlsParaJobXmlDto(url, respuestaHTTPCLient));
                } catch (final UnknownHostException uhe) {
                    this.logger.error("Se está intentando llamar a una URL que no existe: " + url);
                    listaCodigosRespuestaHtpp.add(HttpStatus.NOT_FOUND.value());
                    listaFinalTodo.add(new UrlsParaJobXmlDto(url, HttpStatus.NOT_FOUND.value()));
                }

            }

            xmlAuditoria = new AuditoriaURLDto(listaFinalTodo);
            final Optional<Integer> maxCodeOpt = listaCodigosRespuestaHtpp.stream().max(Comparator.naturalOrder());

            if (!maxCodeOpt.isPresent()) {
                throw new IllegalArgumentException("No se han obtenido códigos de respuesta");
            }

            final Integer maxCode = maxCodeOpt.get();

            if (maxCode >= 200 && maxCode <= 299) {
                xmlAuditoria.setResultado(ResultadoIntegracionEnum.OK);
                xmlAuditoria.setDescripcionResultado("Todo OK");
            } else if (maxCode >= 300 && (maxCode <= 399)) {
                xmlAuditoria.setResultado(ResultadoIntegracionEnum.WARNING);
                xmlAuditoria.setDescripcionResultado("Hay respuestas entre 300 y 399");
            } else if (maxCode >= 400) {
                xmlAuditoria.setResultado(ResultadoIntegracionEnum.ERROR);
                xmlAuditoria.setDescripcionResultado("Errores en las peticiones");
            }

        } catch (final Exception e) {

            final Throwable rootCause = Throwables.getRootCause(e);
            this.logger.error("Error al acceder a la URL", e);
            this.logger.error("Excepción:", rootCause);

            xmlAuditoria.setTrazaExcepcion(rootCause);
            xmlAuditoria.setDescripcionResultado(rootCause.getMessage());
            xmlAuditoria.setResultado(ResultadoIntegracionEnum.ERROR);

        } finally {

            final BneAuditoriaIntegracionDto<AuditoriaURLDto> auditoria = new BneAuditoriaIntegracionDto<>();
            auditoria.setPersistable(true);
            auditoria.setAccion(AccionAuditoriaIntegracionEnum.LLAMADA_A_URLS);
            auditoria.setDatosXml(xmlAuditoria);
            auditoria.setFecha(DateUtils.now());
            auditoria.setResultado(xmlAuditoria.getResultado());
            context.setResult(auditoria);
        }

    }

}
