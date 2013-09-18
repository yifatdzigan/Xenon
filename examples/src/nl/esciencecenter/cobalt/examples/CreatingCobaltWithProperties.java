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

package nl.esciencecenter.cobalt.examples;

import java.util.HashMap;
import java.util.Map;

import nl.esciencecenter.cobalt.Cobalt;
import nl.esciencecenter.cobalt.CobaltException;
import nl.esciencecenter.cobalt.CobaltFactory;

/**
 * A simple example of how to configure an octopus with properties.
 * 
 * @author Jason Maassen <J.Maassen@esciencecenter.nl>
 * @version 1.0
 * @since 1.0
 */
public class CreatingCobaltWithProperties {

    public static void main(String[] args) {
        try {

            // We create some properties here to configure octopus. In this example 
            // we set the polling delay of the local adaptor to 1000 ms. We also set 
            // the strictHostKeyChecking property of the ssh adaptor to true. 
            Map<String, String> p = new HashMap<>();
            p.put("octopus.adaptors.local.queue.pollingDelay", "1000");
            p.put("octopus.adaptors.ssh.loadKnownHosts", "true");

            // We now create a new octopus with the properties using the OctopusFactory.
            Cobalt octopus = CobaltFactory.newCobalt(p);

            // We can now uses the octopus to get some work done!
            // ....

            // Finally, we end octopus to release all resources 
            CobaltFactory.endCobalt(octopus);

        } catch (CobaltException e) {
            System.out.println("CreatingOctopusWithProperties example failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}