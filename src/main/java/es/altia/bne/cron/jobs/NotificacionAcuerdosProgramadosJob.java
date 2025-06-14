package es.altia.bne.cron.jobs;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
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

import es.altia.bne.comun.constantes.ConstantesEntities;
import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.CenUsuariosCentrosDto;
import es.altia.bne.model.entities.dto.EmpEmpresarioDto;
import es.altia.bne.model.entities.dto.IlAgendamientoServiciosDto;
import es.altia.bne.model.entities.dto.IlNotificacionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaNotificacionAcuerdosProgramadosDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaNotificacionAcuerdosProgramadosDto.IlNotificacionXmlDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.model.exception.DataAccessException;
import es.altia.bne.service.api.intermediacion.empleador.servicioEmpleadores.IServicioEmpleadoresService;
import es.altia.bne.service.api.intermediacionweb.notificaciones.IIlNotificacionesService;

@DisallowConcurrentExecution
@Component("notificacionAcuerdosProgramadosJob")
@Scope("prototype")
public class NotificacionAcuerdosProgramadosJob implements Job {

    @Autowired
    private IServicioEmpleadoresService empleadoresService;

    @Autowired
    private IIlNotificacionesService notificacionesService;

    @Value("${bne.integraciones.notificacionAcuerdosProgramados.diasNotificacion}")
    private String diasNotificacion;

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificacionAcuerdosProgramadosJob.class);

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {

        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        final AuditoriaNotificacionAcuerdosProgramadosDto xmlAuditoriaJob = new AuditoriaNotificacionAcuerdosProgramadosDto();
        try {
            LOGGER.info("Proceso de Notificacion de acuerdos programados - Inicio");

            final List<IlNotificacionXmlDto> notificacionesXmlCreadas = new ArrayList<>();
            final List<IlNotificacionXmlDto> notificacionesXmlCaducadas = new ArrayList<>();
            // Se caducan las notificaciones cuya fecha FEC_ACUERDO < al al dia:hora actual
            final List<IlNotificacionDto> notificacionesNoVigentes = this.notificacionesService
                    .getNotificacionesPendientesByTipoAndFechaAcuerdoNoVigente(ConstantesEntities.BNE_IL_TIPO_NOTIFICACION_EMPRESA_CERCANA);
            if (!notificacionesNoVigentes.isEmpty()) {
                for (final IlNotificacionDto notificacion : notificacionesNoVigentes) {
                    this.caducarNotificacionAcuerdoProgramado(notificacion);
                    notificacionesXmlCaducadas.add(this.createNotificacionXml(notificacion));
                }
            }

            // Se recuperan los acuerdos programados cuya fecha_agendada sea hoy + days. Es decir, si se ejecuta hoy y en
            // this.diasNotificacion se configura un 1, se recuperaran todos los acuerdos de manhana desde las 00:00:00 hasta las 23:59:59
            // Para cada uno de estos acuerdos se creara una notificacion en IL_NOTIFICACION de tipo 3 - "Notificación gestión
            // empresa cercana" y estado 1 - "Pendientes de gestionar" cuya fec_acuerdo sera la fecha_agendad + hora_agenda
            final List<IlAgendamientoServiciosDto> listaAcuerdoProgramados = this.empleadoresService
                    .getAcuerdosNotificacionAcuerdosProgramadosJob(new Integer(this.diasNotificacion));
            if (!listaAcuerdoProgramados.isEmpty()) {
                for (final IlAgendamientoServiciosDto acuerdo : listaAcuerdoProgramados) {
                    notificacionesXmlCreadas.add(this.createNotificacionXml(this.guardarNotificacionAcuerdoProgramado(acuerdo)));
                }
            }

            xmlAuditoriaJob.setNotificacionesCreadasList(notificacionesXmlCreadas);
            xmlAuditoriaJob.setNotificacionesCaducadasList(notificacionesXmlCaducadas);

            resultado = ResultadoIntegracionEnum.OK;
            LOGGER.info("Proceso de Notificacion de acuerdos programados - Finaliza correctamente");
        } catch (final Exception e) {
            LOGGER.error("Proceso de Notificacion de acuerdos programados - Finaliza con error no controlado");
            LOGGER.error("Error al procesar una notificacion", e);
            xmlAuditoriaJob.setTrazaExcepcion(e);
            xmlAuditoriaJob.setDescripcionResultado(e.getMessage());
        } finally {
            // Se graba auditoria
            final BneAuditoriaIntegracionDto<AuditoriaNotificacionAcuerdosProgramadosDto> auditoria = new BneAuditoriaIntegracionDto<>();
            auditoria.setAccion(AccionAuditoriaIntegracionEnum.NOTIFICACION_ACUERDOS_PROGRAMADOS);
            auditoria.setPersistable(true);
            auditoria.setFecha(DateUtils.now());
            auditoria.setResultado(resultado);
            auditoria.setDatosXml(xmlAuditoriaJob);
            context.setResult(auditoria);
        }
    }

    private IlNotificacionDto guardarNotificacionAcuerdoProgramado(final IlAgendamientoServiciosDto acuerdo)
            throws ParseException, DataAccessException {

        final IlNotificacionDto notificacion = new IlNotificacionDto();
        final Date fechActual = new Date();

        final Date fechaHoraCita = DateUtils.transformDate(acuerdo.getFechaAgendada().concat(" ").concat(acuerdo.getHoraAgendada()),
                DateUtils.FORMAT_SHORT_DATE.concat(" ").concat(DateUtils.FORMAT_SHORT_TIME));

        notificacion.setIdEstadoNotificacion(ConstantesEntities.BNE_IL_ESTADO_NOTIFICACION_PENDIENTE_GESTIONAR);
        notificacion.setIdTipoNotificacion(ConstantesEntities.BNE_IL_TIPO_NOTIFICACION_EMPRESA_CERCANA);
        notificacion.setFecAlta(fechActual);
        notificacion.setFecModif(fechActual);
        notificacion.setEmpresario(new EmpEmpresarioDto(acuerdo.getIdEmpleador()));
        notificacion.setCenUsuarioCentro(new CenUsuariosCentrosDto(acuerdo.getIdIntermediador()));
        notificacion.setFecAcuerdo(fechaHoraCita);

        notificacion.setId(this.notificacionesService.guardarNotificacion(notificacion));
        return notificacion;

    }

    private void caducarNotificacionAcuerdoProgramado(final IlNotificacionDto notificacion) throws ParseException, DataAccessException {
        notificacion.setIdEstadoNotificacion(ConstantesEntities.BNE_IL_ESTADO_NOTIFICACION_CADUCADA);
        notificacion.setFecModif(new Date());
        this.notificacionesService.updateNotificacion(notificacion);

    }

    private IlNotificacionXmlDto createNotificacionXml(final IlNotificacionDto notificacionDto) {
        return new IlNotificacionXmlDto(notificacionDto.getId(), notificacionDto.getIdEstadoNotificacion(),
                notificacionDto.getIdTipoNotificacion(), notificacionDto.getFecAlta(), notificacionDto.getFecModif(),
                notificacionDto.getCenUsuarioCentro() != null ? notificacionDto.getCenUsuarioCentro().getId() : null,
                notificacionDto.getEmpresario() != null ? notificacionDto.getEmpresario().getId() : null, notificacionDto.getFecAcuerdo());
    }

}
