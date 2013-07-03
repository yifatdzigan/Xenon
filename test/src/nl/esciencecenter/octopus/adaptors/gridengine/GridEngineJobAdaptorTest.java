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

import static org.junit.Assert.assertTrue;

import java.io.OutputStream;
import java.net.URI;

import nl.esciencecenter.octopus.Octopus;
import nl.esciencecenter.octopus.OctopusFactory;
import nl.esciencecenter.octopus.adaptors.GenericJobAdaptorTestParent;
import nl.esciencecenter.octopus.exceptions.InvalidLocationException;
import nl.esciencecenter.octopus.files.AbsolutePath;
import nl.esciencecenter.octopus.files.FileSystem;
import nl.esciencecenter.octopus.files.OpenOption;
import nl.esciencecenter.octopus.files.RelativePath;
import nl.esciencecenter.octopus.jobs.Job;
import nl.esciencecenter.octopus.jobs.JobDescription;
import nl.esciencecenter.octopus.jobs.JobStatus;
import nl.esciencecenter.octopus.jobs.Scheduler;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * @author Niels Drost <N.Drost@esciencecenter.nl>
 * 
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GridEngineJobAdaptorTest extends GenericJobAdaptorTestParent {

    @BeforeClass
    public static void prepareGridEngineJobAdaptorTest() throws Exception {
        GenericJobAdaptorTestParent.prepareClass(new GridEngineJobTestConfig(null));
    }

    @AfterClass
    public static void cleanupGridEngineJobAdaptorTest() throws Exception {
        GenericJobAdaptorTestParent.cleanupClass();
    }
    
    @org.junit.Test
    public void ge_test01_jobWithCustomScript() throws Exception {
        String message = "Hello World! test01\n";
        
        String workingDir = getWorkingDir("ge_test01");
        
        Scheduler scheduler = config.getDefaultScheduler(jobs, credentials);
        FileSystem filesystem = config.getDefaultFileSystem(files, credentials);
        
        AbsolutePath root = filesystem.getEntryPath().resolve(new RelativePath(workingDir));
        files.createDirectories(root);
        
        AbsolutePath script = root.resolve(new RelativePath("script"));
        AbsolutePath stdout = root.resolve(new RelativePath("stdout.txt"));
        
        String scriptContent = "#!/bin/bash\n" +
                "#$ -o " + stdout.getPath() + "\n"
                + "#$ -e /dev/null\n"
                + "echo " + message;

        OutputStream out = files.newOutputStream(script, OpenOption.CREATE, OpenOption.APPEND, OpenOption.WRITE);
        writeFully(out, scriptContent);
        
        JobDescription description = new JobDescription();
        description.setInteractive(false);
        description.getJobOptions().put("ge.job.script", script.getPath());
        //FIXME: limitation of ge adaptor.
        description.setExecutable("ignored");
        
        Job job = jobs.submitJob(scheduler, description);
        JobStatus status = jobs.waitUntilDone(job, 60000);
        
        if (!status.isDone()) { 
            throw new Exception("Job exceeded deadline!");
        }
        
        if (status.hasException()) {
            throw new Exception("Job failed!", status.getException());
        }
        
        String outputContent = readFully(files.newInputStream(stdout));

        files.delete(stdout);
        files.delete(script);           
        files.delete(root);        
        
        jobs.close(scheduler);
        files.close(filesystem);
        
        assertTrue(outputContent.equals(message));
    }
    
    @Test
    public void ge_test02_newScheduler_pathWithSlash() throws Exception {
        
        URI uriWithSlash = new URI(config.getCorrectURI().toString() + "/");
        
        Scheduler s = jobs.newScheduler(uriWithSlash, null, null);
        jobs.close(s);
    }

    @Test(expected = InvalidLocationException.class)
    public void ge_test03_newScheduler_pathWithFragment_Exception() throws Exception {
        
        URI uriWithFragment = new URI(config.getCorrectURI().toString() + "#somefragment");
        
        Scheduler s = jobs.newScheduler(uriWithFragment, null, null);
        jobs.close(s);
    }

    

}