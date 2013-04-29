/*
 * Copyright 2013 Netherlands eScience Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.esciencecenter.octopus.adaptors.gridengine;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import nl.esciencecenter.octopus.credentials.Credential;
import nl.esciencecenter.octopus.engine.OctopusProperties;
import nl.esciencecenter.octopus.engine.util.StreamForwarder;
import nl.esciencecenter.octopus.exceptions.InvalidLocationException;
import nl.esciencecenter.octopus.exceptions.OctopusException;
import nl.esciencecenter.octopus.exceptions.OctopusIOException;
import nl.esciencecenter.octopus.exceptions.UnknownPropertyException;
import nl.esciencecenter.octopus.jobs.Job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchedulerConnection {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerConnection.class);

    private static int schedulerID = 0;

    private static synchronized int getNextSchedulerID() {
        return schedulerID++;
    }

    private final URI actualLocation;

    private final XmlOutputParser parser;

    private final OctopusProperties properties;
    private final String[] queueNames;

    private String id;

    private Process runCommandAtServer(String command, String stdin) throws OctopusException, OctopusIOException {
        try {
            if (actualLocation.getHost() != null) {
                command = "ssh " + actualLocation.getHost() + " " + command;
            }
            logger.debug("running command " + command);
            Process process = Runtime.getRuntime().exec(command);

            if (stdin != null) {
                byte[] bytes = stdin.getBytes();
                InputStream in = new ByteArrayInputStream(bytes);

                new StreamForwarder(in, process.getOutputStream());
            }

            return process;
        } catch (IOException e) {
            throw new OctopusIOException(GridengineAdaptor.ADAPTOR_NAME, "cannot execute remote process", e);
        }
    }

    public SchedulerConnection(URI location, Credential credential, Properties properties) throws OctopusIOException,
            OctopusException {
        this.properties = new OctopusProperties(properties);

        if (properties != null && properties.size() > 0) {
            throw new UnknownPropertyException(GridengineAdaptor.ADAPTOR_NAME,
                    "grid engine scheduler does not support any property");
        }

        GridengineAdaptor.checkLocation(location);
        try {

            id = GridengineAdaptor.ADAPTOR_NAME + "-" + getNextSchedulerID();

            parser = new XmlOutputParser(this.properties);

            if (location.getHost() == null || location.getHost().length() == 0) {
                //FIXME: check if this works for encode uri's, illegal characters, fragments, etc..
                actualLocation = new URI("local:///");
            } else {
                actualLocation = new URI("ssh", location.getSchemeSpecificPart(), location.getFragment());
            }
        } catch (URISyntaxException e) {
            throw new OctopusException(GridengineAdaptor.ADAPTOR_NAME, "cannot create ssh uri from given location", e);
        }

        //get status of all queues, use names to fill queue name list
        this.queueNames = getQueueStatus().keySet().toArray(new String[0]);

        logger.debug("queues for " + location + " are " + Arrays.toString(this.queueNames));
    }

    //local operation
    public String[] getQueueNames() {
        return queueNames;
    }

    public OctopusProperties getProperties() {
        return properties;
    }

    public String getID() {
        return id;
    }

    public Map<String, Map<String, String>> getQueueStatus() throws OctopusException, OctopusIOException {
        try (InputStream in = runCommandAtServer("qstat -xml -g c", null).getInputStream()) {
            return parser.parseQueueInfos(in);
        } catch (IOException e) {
            throw new OctopusIOException(GridengineAdaptor.ADAPTOR_NAME, "could not get status of queues from server", e);
        }
    }

    public Map<String, Map<String, String>> getJobStatus() throws OctopusException, OctopusIOException {
        try (InputStream in = runCommandAtServer("qstat -xml", null).getInputStream()) {
            return parser.parseJobInfos(in);
        } catch (IOException e) {
            throw new OctopusIOException(GridengineAdaptor.ADAPTOR_NAME, "could not get status of jobs from server", e);
        }
    }

    public Map<String, String> getJobAccountingInfo(String jobIdentifier) throws OctopusIOException, OctopusException {
        Process process = runCommandAtServer("qacct -j " + jobIdentifier, null);

        try (InputStream stdout = process.getInputStream(); InputStream stderr = process.getErrorStream()) {
            return TxtOutputParser.getJobAccountingInfo(stdout, stderr);
        } catch (IOException e) {
            throw new OctopusIOException(GridengineAdaptor.ADAPTOR_NAME, "could not get status of jobs from server", e);
        }
    }

    /**
     * Submit a job to a GridEngine machine. Mostly involves parsing output
     * 
     * @param jobScript
     *            the script to submit
     * @return the id of the job
     * @throws OctopusException
     *             if the qsub command could not be run
     * @throws OctopusIOException
     *             if the qsub command could not be run, or the jobID could not be read from the output
     */
    public String submitJob(String jobScript) throws OctopusException, OctopusIOException {
        Process process = runCommandAtServer("qsub", jobScript);
        try (InputStream stdout = process.getInputStream(); InputStream stderr = process.getErrorStream()) {
            return TxtOutputParser.checkSubmitJobResult(stdout, stderr);
        } catch (OctopusIOException e) {
            throw e;
        } catch (IOException e) {
            throw new OctopusIOException(GridengineAdaptor.ADAPTOR_NAME, "could not submit job to server", e);
        }
    }

    /**
     * Cancel a job.
     * 
     * @param job
     *            the job to cancel
     * @throws OctopusException
     *             if the qsub command could not be run
     * @throws OctopusIOException
     *             if the qsub command could not be run, or the jobID could not be read from the output
     */
    public void cancelJob(Job job) throws OctopusIOException, OctopusException {
        Process process = runCommandAtServer("qdel " + job.getIdentifier(), null);

        try (InputStream stdout = process.getInputStream(); InputStream stderr = process.getErrorStream()) {
            TxtOutputParser.checkCancelJobResult(job.getIdentifier(), stdout, stderr);
        } catch (OctopusIOException e) {
            throw e;
        } catch (IOException e) {
            throw new OctopusIOException(GridengineAdaptor.ADAPTOR_NAME, "could not delete job " + job.getIdentifier()
                    + " at server", e);
        }
    }

    public void close() {
        //FIXME: close ssh connection to server here

    }

};