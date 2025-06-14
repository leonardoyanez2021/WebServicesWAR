package es.altia.bne.cron.jobs;

import es.altia.bne.comun.exception.BNEException;

public class ProcesaCertificacionesJobException extends BNEException {
	
	private static final long serialVersionUID = 1L;
    private String codigo;

    /**
     * Constructor que recibe únicamente un mensaje.
     *
     * @param msg
     */
    public ProcesaCertificacionesJobException(final String message) {
        super(message);
    }

    /**
     * Constructor con mensaje y causa de origen.
     *
     * @param message
     * @param cause
     */
    public ProcesaCertificacionesJobException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ProcesaCertificacionesJobException(final String msg, final String campo) {
        super(msg + "Codigo: " + campo);
        this.codigo = campo;
    }

    public ProcesaCertificacionesJobException(final Throwable t, final String campo) {
        super(campo, t);
        this.codigo = campo;
    }

    public ProcesaCertificacionesJobException(final String msg, final Throwable t, final String campo) {
        super(msg, t);
        this.codigo = campo;
    }

    public String getCodigo() {
        return this.codigo;
    }

    public void setCodigo(final String campo) {
        this.codigo = campo;
    }	

}
