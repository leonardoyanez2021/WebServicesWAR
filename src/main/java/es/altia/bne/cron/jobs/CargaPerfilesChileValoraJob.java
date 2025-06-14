package es.altia.bne.cron.jobs;

import java.util.ArrayList;
import java.util.List;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaCargaPerfilesChileValoraDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.service.chilevalora.IChileValoraService;
import es.altia.bne.service.chilevalora.dto.ObtenerPerfilesChileValoraDto;
import es.altia.bne.service.chilevalora.dto.PerfilesChileValoraDto;

@Component("jobCargaPerfilesChileValora")
@Scope("prototype")
@DisallowConcurrentExecution
public class CargaPerfilesChileValoraJob implements Job {

    @Value("${bne.integraciones.tallerAprestos.numDias.correo}")
    private int diasCorreo;
    @Value("${bne.integraciones.tallerAprestos.numDias.actualizarEstado}")
    private int diasUpdateEstado;

    private static final Logger LOGGER = LoggerFactory.getLogger(CargaPerfilesChileValoraJob.class);

    @Autowired
    IChileValoraService chileValoraService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        LOGGER.info("Iniciando proceso de carga de certificaciones de chile valora");
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaCargaPerfilesChileValoraDto xmlAuditoria = new AuditoriaCargaPerfilesChileValoraDto();
        try {
            LOGGER.info("Consultando perfiles a ChileValora");
            final ObtenerPerfilesChileValoraDto perfilesChileValora = this.chileValoraService.getPerfilesChileValora();

            LOGGER.info("Consultando perfiles cargados en BBDD");
            final List<String> certificacionesCargadas = this.chileValoraService.getCertificacionesCargadas();
            final List<PerfilesChileValoraDto> perfilesErroneos = new ArrayList<>();
            Integer codigo = this.chileValoraService.getCodigoGenericoMigracion();
            int certificacionesInsertadas = 0;
            LOGGER.info("Insertando  nuevos perfiles en BBDD");
            for (final PerfilesChileValoraDto certificacionChileValora : perfilesChileValora.getPerfiles()) {
                final boolean insertar = true;
                if (!certificacionesCargadas.contains(certificacionChileValora.getCodPerfil())) {
                    try {
                        codigo = codigo + 1;
                        this.chileValoraService.insertarPerfiles(certificacionChileValora, codigo);
                        certificacionesInsertadas++;
                    } catch (final Exception e) {
                        perfilesErroneos.add(certificacionChileValora);
                    }
                }
            }
            if (!perfilesErroneos.isEmpty()) {
                final ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
                xmlAuditoria.setPerfilesErroneosJson(ow.writeValueAsString(perfilesErroneos));
            }
            xmlAuditoria.setNumeroPerfilesCargados(certificacionesInsertadas);
            xmlAuditoria.setNumeroPerfilesErroneos(perfilesErroneos.size());
            resultado = ResultadoIntegracionEnum.OK;

            LOGGER.info("Auditando datos de participantes que asistieron al taller");

        } catch (final Exception e) {
            LOGGER.error("Proceso nocturno carga certificaciones chile valora - Finaliza con error no controlado");
            xmlAuditoria = new AuditoriaCargaPerfilesChileValoraDto();
            xmlAuditoria.setTrazaExcepcion(e);
            xmlAuditoria.setDescripcionResultado(e.getMessage());
            resultado = ResultadoIntegracionEnum.ERROR;

        } finally {
            final BneAuditoriaIntegracionDto<AuditoriaCargaPerfilesChileValoraDto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.CARGA_PERFILES_CHILE_VALORA);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }

    }
}
