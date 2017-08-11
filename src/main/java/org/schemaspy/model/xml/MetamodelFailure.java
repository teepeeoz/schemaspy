/*
 */
package org.schemaspy.model.xml;

/**
 * Indicates that we couldn't load or process the meat model
 *
 * @author John Currier
 */
public class MetamodelFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /**
     * When a message is sufficient
     *
     * @param msg
     */
    public MetamodelFailure(String msg) {
        super(msg);
    }

    /**
     * When there's an associated root cause.
     * The resultant msg will be a combination of <code>msg</code> and cause's <code>msg</code>.
     *
     * @param msg
     * @param cause
     */
    public MetamodelFailure(String msg, Throwable cause) {
        super(msg + " " + cause.getMessage(), cause);
    }

    /**
     * When there are no details other than the root cause
     *
     * @param cause
     */
    public MetamodelFailure(Throwable cause) {
        super(cause);
    }
}
