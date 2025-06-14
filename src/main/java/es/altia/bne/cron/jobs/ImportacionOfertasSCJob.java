package es.altia.bne.cron.jobs;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaImportacionOfertasSCDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.service.IImportacionOfertasScService;

@DisallowConcurrentExecution
@Component("jobImportacionOfertasSc")
@Scope("prototype")
public class ImportacionOfertasSCJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportacionOfertasSCJob.class);

    @Autowired
    private IImportacionOfertasScService importacionOfertasScService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaImportacionOfertasSCDto xmlAuditoria = null;
        try {
            LOGGER.info("Proceso de importación de ofertas del Servicio Civil - Inicio");
            // TODO modificar resultado de importarOfertasSC a AuditoriaImportacionOfertasSCDto
            // certificar que el restulado WARNING/ERROR/OK se puede sacar del DTO que se cree, y calcularlo después en vez de recibibirlo
            xmlAuditoria = this.importacionOfertasScService.importarOfertasSc();
            this.logResultadoImportacion(xmlAuditoria.getResultado());
            resultado = ResultadoIntegracionEnum.OK;
            LOGGER.info("Proceso de importación de ofertas del Servicio Civil - Finaliza correctamente");
        } catch (final Exception e) {
            LOGGER.info("Proceso de importación de ofertas del Servicio Civil - Finaliza con error no controlado");
            xmlAuditoria = new AuditoriaImportacionOfertasSCDto();
            xmlAuditoria.setTrazaExcepcion(e);
            xmlAuditoria.setDescripcionResultado(e.getMessage());
            resultado = ResultadoIntegracionEnum.ERROR;
        } finally {
            // Grabamos auditoría
            final BneAuditoriaIntegracionDto<AuditoriaImportacionOfertasSCDto> a = new BneAuditoriaIntegracionDto<>();
            a.setAccion(AccionAuditoriaIntegracionEnum.IMPORTACION_OFERTAS_EMPLEO_CIVIL);
            a.setFecha(new java.util.Date());
            a.setResultado(xmlAuditoria.getResultado() != null ? xmlAuditoria.getResultado() : resultado);
            a.setDatosXml(xmlAuditoria);
            a.setFichero(xmlAuditoria.getFicheroCSV());
            context.setResult(a);
        }
    }

    private void logResultadoImportacion(final ResultadoIntegracionEnum resultadoImportacion) {
        switch (resultadoImportacion) {
        case OK:
            LOGGER.error("Importación finalizada con estado OK. Datos guardados en auditoría de integración.");
            break;

        case WARNING:
            LOGGER.error("Importación finalizada con estado WARNING. Datos guardados en auditoría de integración.");
            break;

        case ERROR:
            LOGGER.error("Importación finalizada con estado ERROR. Datos guardados en auditoría de integración.");
            break;

        default:
            LOGGER.error("Importación finalizada con error no controlado. Datos no guardados en auditoría de integración");
            break;
        }
    }

}
