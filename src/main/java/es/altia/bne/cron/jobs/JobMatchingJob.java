package es.altia.bne.cron.jobs;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.base.Throwables;

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.CasacionJobMatchingDto;
import es.altia.bne.model.entities.dto.RecalculoJobMatchingOfertaDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaJobMatchingDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.service.IJobMatchingService;

@DisallowConcurrentExecution
@Component("jobJobMatching")
@Scope("prototype")
public class JobMatchingJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobMatchingJob.class);
    private static final String ANTOFAGASTA = "ANTOFAGASTA";
    private static final String BNE = "BNE";
    @Autowired
    private IJobMatchingService jobMatchingService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        final AuditoriaJobMatchingDto xmlAuditoriaJm = new AuditoriaJobMatchingDto();

        try {
            LOGGER.info("Proceso de Job Matching- Inicio");
            /*
             * Se recuperan las ofertas a procesar.
             *
             * Para cada oferta, se ejecuta en una transacción independiente el proceso de asociación de demandantes a oferta.
             *
             * Cada invitación por mail se ejecuta en una transacción independiente.
             */
            final List<Long> listaIdOfertaProcesar = this.jobMatchingService.obtenerListaIdOfertaJobMatching();
            listaIdOfertaProcesar.forEach(idOferta -> {
                final List<CasacionJobMatchingDto> rankingInsertado = this.jobMatchingService.ejecutarJobMatchingOferta(idOferta, BNE);
                this.jobMatchingService.enviarMailCandidatos(rankingInsertado, BNE);
                this.jobMatchingService.enviarSmsCandidatos(rankingInsertado, BNE);

                if (!rankingInsertado.isEmpty()) {
                    xmlAuditoriaJm.addCasacion(idOferta, rankingInsertado.get(0).getCodOferta(), rankingInsertado.size());
                } else {
                    xmlAuditoriaJm.addCasacion(idOferta, "N/A", rankingInsertado.size());
                }
            });

            // TODO DESCOMENTAR ANTOFAGASTA
            // Se recuperan las ofertas a procesar.

            // Para cada oferta, se ejecuta en una transacción independiente el proceso de asociación de demandantes a oferta.

            // Cada invitación por mail se ejecuta en una transacción independiente.

            final List<Long> listaIdOfertaProcesarAntofagasta = this.jobMatchingService.obtenerListaIdOfertaAntofagastaJobMatching();
            listaIdOfertaProcesarAntofagasta.forEach(idOferta -> {
                final List<CasacionJobMatchingDto> rankingInsertado = this.jobMatchingService.ejecutarJobMatchingOferta(idOferta,
                        ANTOFAGASTA);
                this.jobMatchingService.enviarMailCandidatos(rankingInsertado, ANTOFAGASTA);
                this.jobMatchingService.enviarSmsCandidatos(rankingInsertado, ANTOFAGASTA);

                if (!rankingInsertado.isEmpty()) {
                    xmlAuditoriaJm.addCasacion(idOferta, rankingInsertado.get(0).getCodOferta(), rankingInsertado.size());
                } else {
                    xmlAuditoriaJm.addCasacion(idOferta, "N/A", rankingInsertado.size());
                }
            });

            // Se recuperan las ofertas a reprocesar y también se procesa cada una en una transacción independiente.

            final List<RecalculoJobMatchingOfertaDto> listaIdOfertaReprocesar = this.jobMatchingService
                    .obtenerListaIdOfertaRecalculoJobMatching();
            listaIdOfertaReprocesar.forEach(oferta -> {
                final List<CasacionJobMatchingDto> rankingInsertado = this.jobMatchingService.ejecutarRecalculoJobMatchingOferta(oferta);
                if (!rankingInsertado.isEmpty()) {
                    //@formatter:off
                    final List<AuditoriaJobMatchingDto.PostulanteRecalculado> postulantes = rankingInsertado.stream()
                            .map(r -> {
                                return new AuditoriaJobMatchingDto.PostulanteRecalculado(r.getIdPersona(), r.getRut(),
                                        r.getPorcentajeCoincidencia().doubleValue());
                            })
                            .collect(Collectors.toCollection(LinkedList::new));
                    //@formatter:on
                    xmlAuditoriaJm.addRecalculo(oferta.getIdOferta(), rankingInsertado.get(0).getCodOferta(), postulantes);
                } else {
                    xmlAuditoriaJm.addRecalculo(oferta.getIdOferta(), "N/A", Collections.emptyList());
                }
            });

            resultado = ResultadoIntegracionEnum.OK;
            LOGGER.info("Proceso Job Matching - Finaliza correctamente");
        } catch (final Exception e) {
            LOGGER.error("Proceso Job Matching - Finaliza con error no controlado");
            // xmlAuditoria.setTrazaExcepcion(e);// TODO << ver si se serializa bien !!!!!!!!
            xmlAuditoriaJm.setDescripcionResultado(e.getMessage());
            throw new JobExecutionException(Throwables.getRootCause(e));
        } finally {
            // Grabamos auditoría
            final BneAuditoriaIntegracionDto<AuditoriaJobMatchingDto> a = new BneAuditoriaIntegracionDto<>();
            a.setAccion(AccionAuditoriaIntegracionEnum.JOB_MATCHING);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoriaJm);
            context.setResult(a);
        }
    }
}
