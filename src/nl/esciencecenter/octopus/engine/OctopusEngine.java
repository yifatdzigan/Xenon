package nl.esciencecenter.octopus.engine;

import java.util.Properties;
import java.util.Vector;

import nl.esciencecenter.octopus.AdaptorInfo;
import nl.esciencecenter.octopus.OctopusProperties;
import nl.esciencecenter.octopus.Octopus;
import nl.esciencecenter.octopus.engine.files.FilesEngine;
import nl.esciencecenter.octopus.engine.jobs.JobsEngine;
import nl.esciencecenter.octopus.exceptions.OctopusException;
import nl.esciencecenter.octopus.files.Files;
import nl.esciencecenter.octopus.jobs.Jobs;
import nl.esciencecenter.octopus.security.Credentials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of Octopus Interface class. Redirects calls to adaptors, and the FilesEngine
 * 
 * @author Niels Drost
 */
public class OctopusEngine implements Octopus {

    private static final Logger logger = LoggerFactory.getLogger(OctopusEngine.class);

    // list of all octopusEngines, so we can end them all in one go.
    private static final Vector<OctopusEngine> octopusEngines = new Vector<OctopusEngine>();

    public static Octopus newEngine(Properties properties, Credentials credentials) throws OctopusException {
        OctopusEngine result = new OctopusEngine(properties, credentials);

        octopusEngines.add(result);

        return result;
    }

    public static void endEngines() {
        for (OctopusEngine octopusEngine : octopusEngines) {
            octopusEngine.end();
        }
    }

    private boolean ended = false;

    private Credentials defaultCredentials = new Credentials();

    private Properties defaultProperties = new Properties();

    private final FilesEngine filesEngine;

    private final JobsEngine jobsEngine;

    private final Adaptor[] adaptors;

    /**
     * Constructs a OctopusEngine instance.
     * 
     * @param credentials
     *            the credentials to use. Will NOT be copied.
     * @param properties
     *            the properties to use. Will NOT be copied.
     * @throws OctopusException
     */
    private OctopusEngine(Properties properties, Credentials credentials) throws OctopusException {
        if (properties == null) {
            defaultProperties = new Properties();
        } else {
            defaultProperties = properties;
        }

        if (credentials == null) {
            defaultCredentials = new Credentials();
        } else {
            defaultCredentials = credentials;
        }

        adaptors = AdaptorLoader.loadAdaptors(new OctopusProperties(defaultProperties), this);

        filesEngine = new FilesEngine(this);

        jobsEngine = new JobsEngine(this);

        logger.info("Octopus engine initialized with adaptors: " + adaptors);
    }

    public synchronized OctopusProperties getCombinedProperties(Properties properties) {
        OctopusProperties result = new OctopusProperties(defaultProperties, properties);

        return result;
    }

    public synchronized Credentials getCombinedCredentials(Credentials credentials) {
        // read only credentials
        Credentials result = new Credentials(true, defaultCredentials, credentials);

        return result;
    }

    // ************** Octopus Interface Implementation ***************\\

    @Override
    public AdaptorInfo[] getAdaptorInfos() {
        return adaptors.clone();
    }

    @Override
    public AdaptorInfo getAdaptorInfo(String adaptorName) throws OctopusException {
        return getAdaptor(adaptorName);
    }

    /**
     * Return the adaptor that provides functionality for the given scheme.
     * 
     * @param scheme
     *            the scheme for which to get the adaptor
     * @return the adaptor
     */
    public Adaptor getAdaptorFor(String scheme) throws OctopusException {
        for (Adaptor adaptor : adaptors) {
            if (adaptor.supports(scheme)) {
                return adaptor;
            }
        }
        throw new OctopusException("cannot find adaptor for scheme " + scheme, null, null);
    }

    public Adaptor getAdaptor(String name) throws OctopusException {
        for (Adaptor adaptor : adaptors) {
            if (adaptor.getName().equals(name)) {
                return adaptor;
            }
        }

        throw new OctopusException("could not find adaptor named " + name, null, null);
    }

    @Override
    public synchronized Credentials getDefaultCredentials() {
        return defaultCredentials;
    }

    @Override
    public synchronized void setDefaultCredentials(Credentials credentials) {
        defaultCredentials = credentials;
    }

    @Override
    public synchronized Properties getDefaultProperties() {
        return defaultProperties;
    }

    @Override
    public synchronized void setDefaultProperties(Properties properties) {
        defaultProperties = properties;
    }

    @Override
    public Files files() {
        return filesEngine;
    }

    @Override
    public Jobs jobs() {
        return jobsEngine;
    }

    @Override
    public void end() {
        synchronized (this) {
            if (ended) {
                return;
            }

            ended = true;
        }

        for (Adaptor adaptor : adaptors) {
            adaptor.end();
        }
    }

}
