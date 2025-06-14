package es.altia.bne.cron.jobs;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaEnvioInformacionAccionesSenceDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.service.api.jobs.sence.IEnvioInformacionAccionesIntermediacionService;

@Component("jobEnvioInformacionAccionesSence")
@Scope("prototype")
public class EnvioInformacionAccionesSenceJob implements Job {

    final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IEnvioInformacionAccionesIntermediacionService envioAccionesIntermediacion;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        final SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS");
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaEnvioInformacionAccionesSenceDto xmlAuditoria = null;
        Date fechaEjecucion;
        try {
            fechaEjecucion = DateUtils.now();
            String respuestaProcedure = "";
            this.envioAccionesIntermediacion.generarXMLAcciones();
            respuestaProcedure = sdf.format(new Date());
            fechaEjecucion = DateUtils.now();
            xmlAuditoria = new AuditoriaEnvioInformacionAccionesSenceDto(fechaEjecucion, ResultadoIntegracionEnum.OK.getId(),
                    ResultadoIntegracionEnum.OK.getDescripcion(), respuestaProcedure);
            resultado = ResultadoIntegracionEnum.OK;
        } catch (final Exception e) {
            xmlAuditoria = new AuditoriaEnvioInformacionAccionesSenceDto();
            xmlAuditoria.setTrazaExcepcion(e);
            xmlAuditoria.setDescripcionResultado(e.getMessage());
            resultado = ResultadoIntegracionEnum.ERROR;
        } finally {
            final BneAuditoriaIntegracionDto<AuditoriaEnvioInformacionAccionesSenceDto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.ENVIO_ACCIONES_INTERMEDIACION_SENCE);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }
    }

}
