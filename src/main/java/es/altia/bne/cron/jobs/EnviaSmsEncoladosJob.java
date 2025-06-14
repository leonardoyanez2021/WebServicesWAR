package es.altia.bne.cron.jobs;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hibernate.HibernateException;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;

import es.altia.bne.comun.constantes.SmsDeliveryType;
import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.dto.ResultadoEnvioSmsDto;
import es.altia.bne.model.dao.entities.ISctPerSectoresDAO;
import es.altia.bne.model.entities.SmsCeEnvios;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaEnviaSmsEncoladosDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.model.exception.DataAccessException;
import es.altia.bne.service.sms.IBneSmsQueueService;
import es.altia.bne.service.sms.ISmsGatewayService;
import es.altia.bne.service.sms.SmsGatewayService;

@DisallowConcurrentExecution
@Component("jobEnviaSmsEncolados")
@Scope("prototype")
public class EnviaSmsEncoladosJob implements Job {

    @Autowired
    private ISctPerSectoresDAO sctPerSectoresDAO;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        final AuditoriaEnviaSmsEncoladosDto xmlAuditoria = new AuditoriaEnviaSmsEncoladosDto();

        try {
            final Stopwatch sw = Stopwatch.createStarted();
            LOGGER.info("Iniciando proceso de envío de SMS encolados");
            final List<SmsCeEnvios> toSendEsCchc = this.bneSmsQueueService.getSmsEncoladosEsCchc();
            final List<SmsCeEnvios> toSendNoEsCchc = this.bneSmsQueueService.getSmsEncoladosNoEsCchc();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Encontrados {} SMS encolados", toSendEsCchc.size());
                LOGGER.debug("Encontrados {} SMS encolados", toSendNoEsCchc.size());
            }
            long enviadosConExito = 0;
            long enviadosConError = 0;

            if (!toSendEsCchc.isEmpty()) {
                // Diferenciamos los mensajes por tipo de envío - las pasarelas son diferentes
                final ListMultimap<SmsDeliveryType, SmsCeEnvios> mensajesClasificados = LinkedListMultimap.create();
                toSendEsCchc.forEach(sms -> mensajesClasificados.put(SmsDeliveryType.fromIdTipoEnvio(sms.getIdTipoEnvio()), sms));

                // Enviamos y procesamos los mensajes en bloques
                for (final SmsDeliveryType tipoEntrega : mensajesClasificados.keySet()) {
                    for (final List<SmsCeEnvios> listaSms : Iterables.partition(mensajesClasificados.get(tipoEntrega),
                            SmsGatewayService.MAX_SMS_PER_BLOCK)) {
                        final ResultadoEnvioSmsDto estadoEnvio = this.smsGatewayService.enviaMensajes(listaSms);
                        enviadosConExito += estadoEnvio.getEnviadosConExito();
                        enviadosConError += estadoEnvio.getEnviadosConError();
                    }
                }
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("No se envían mensajesEncontrados {} SMS encolados", toSendEsCchc.size());
                }
            }

            if (!toSendNoEsCchc.isEmpty()) {
                // Diferenciamos los mensajes por tipo de envío - las pasarelas son diferentes
                final ListMultimap<SmsDeliveryType, SmsCeEnvios> mensajesClasificados = LinkedListMultimap.create();
                toSendNoEsCchc.forEach(sms -> mensajesClasificados.put(SmsDeliveryType.fromIdTipoEnvio(sms.getIdTipoEnvio()), sms));

                // Enviamos y procesamos los mensajes en bloques
                for (final SmsDeliveryType tipoEntrega : mensajesClasificados.keySet()) {
                    for (final List<SmsCeEnvios> listaSms : Iterables.partition(mensajesClasificados.get(tipoEntrega),
                            SmsGatewayService.MAX_SMS_PER_BLOCK)) {
                        final ResultadoEnvioSmsDto estadoEnvio = this.smsGatewayService.enviaMensajesITDChile(listaSms);
                        enviadosConExito += estadoEnvio.getEnviadosConExito();
                        enviadosConError += estadoEnvio.getEnviadosConError();
                    }
                }
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("No se envían mensajesEncontrados {} SMS encolados", toSendNoEsCchc.size());
                }
            }

            xmlAuditoria.setNumeroSMSMasivoEnviadosExito(enviadosConExito);
            xmlAuditoria.setNumeroSMSMasivoEnviadosError(enviadosConError);

            resultado = ResultadoIntegracionEnum.OK;
            LOGGER.info("Envío de SMS encolados finalizado en {}ms", sw.elapsed(TimeUnit.MILLISECONDS));
        } catch (final DataAccessException | HibernateException e) {
            final Throwable rootCause = Throwables.getRootCause(e);
            LOGGER.error("Error al enviar SMS encolados {}", e.getClass().getSimpleName());
            LOGGER.error("Excepción:", rootCause);
            xmlAuditoria.setTrazaExcepcion(rootCause);
        } finally {
            // Grabamos auditoría
            final BneAuditoriaIntegracionDto<AuditoriaEnviaSmsEncoladosDto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.ENVIA_SMS_ENCOLADOS);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(EnviaSmsEncoladosJob.class);

    @Autowired
    private IBneSmsQueueService bneSmsQueueService;

    @Autowired
    private ISmsGatewayService smsGatewayService;
}
