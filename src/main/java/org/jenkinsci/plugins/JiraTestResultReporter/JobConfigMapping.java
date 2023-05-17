/**
 Copyright 2015 Andrei Tuicu

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.jenkinsci.plugins.JiraTestResultReporter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import hudson.model.Job;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.JiraTestResultReporter.config.AbstractFields;
import org.jenkinsci.plugins.JiraTestResultReporter.config.FieldConfigsJsonAdapter;
import org.jenkinsci.plugins.JiraTestResultReporter.config.StringFields;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import static org.jenkinsci.plugins.JiraTestResultReporter.JiraTestDataPublisher.JiraTestDataPublisherDescriptor.*;

/**
 * Created by tuicu.
 * Map from job name to configurations. A new publisher is created every time save/apply is clicked in the job config
 * page, but the new configurations don't apply to previous build, so we need this map in order for all the publishers
 * to be able to access the last configuration. Implemented as a singleton pattern. The map gets serialized every time
 * a new configuration is added
 */
public class JobConfigMapping {
    public static class JobConfigEntry implements Serializable {
        public static final long serialVersionUID = 6509568994710878311L; //backwards compatibility
        protected String projectKey;
        protected Long issueType;
        protected String storeCacheJobName;
        protected List<AbstractFields> configs;
        protected boolean autoRaiseIssue;
        protected boolean overrideResolvedIssues;
        protected boolean autoResolveIssue;
        protected boolean autoUnlinkIssue;
        protected transient Pattern issueKeyPattern;

        /**
         * Constructor
         * @param projectKey
         * @param issueType
         * @param storeCacheJobName
         * @param configs list with the configured fields
         */
        public JobConfigEntry(String projectKey, Long issueType, List<AbstractFields> configs,
                              boolean autoRaiseIssue, boolean autoResolveIssue, boolean autoUnlinkIssue,
                              boolean overrideResolvedIssues) {
            this.projectKey = projectKey;
            this.issueType = issueType;
            this.storeCacheJobName = storeCacheJobName;
            this.configs = configs;
            this.autoRaiseIssue = autoRaiseIssue;
            this.autoResolveIssue = autoResolveIssue;
            this.autoUnlinkIssue = autoUnlinkIssue;
            this.overrideResolvedIssues = overrideResolvedIssues;
            compileIssueKeyPattern();
        }

        /**
         * Getter for the issue type
         * @return
         */
        public Long getIssueType() {
            return issueType;
        }

        /**
         * Getter for the project key
         * @return
         */
        public String getProjectKey() {
            return projectKey;
        }

        /**
         * Getter for the store cache job name
         * @return
         */
        public String getStoreCacheJobName() {
            return storeCacheJobName;
        }

        /**
         * Getter for the configured fields
         * @return
         */
        public List<AbstractFields> getConfigs() {
            return configs;
        }

        public boolean getAutoRaiseIssue() { return autoRaiseIssue; }

        public boolean getOverrideResolvedIssues() { return overrideResolvedIssues; }

        public boolean getAutoResolveIssue() { return  autoResolveIssue; }

        public boolean getAutoUnlinkIssue() { return autoUnlinkIssue; }

        /**
         * Getter for the issue key pattern
         * @return
         */
        public Pattern getIssueKeyPattern() { return issueKeyPattern; }

        /**
         * Method for resolving transient objects after deserialization. Called by the JVM.
         * See Java documentation for more details.
         * @return this object
         */
        private Object readResolve() {
            compileIssueKeyPattern();
            return this;
        }

        protected void compileIssueKeyPattern() {
            this.issueKeyPattern = projectKey !=  null ? Pattern.compile(projectKey + "-\\d+") : null;
        }
    }

    /**
     * Builder fot a JobConfigEntry
     */
    public static class JobConfigEntryBuilder extends JobConfigEntry {
        /**
         * Constructor
         */
        public JobConfigEntryBuilder() {
            super(null, null, new ArrayList<>(), false, false, false, false);
        }

        public JobConfigEntryBuilder withProjectKey(String projectKey) {
            this.projectKey = projectKey;
            compileIssueKeyPattern();
            return this;
        }

        public JobConfigEntryBuilder withIssueType(Long issueType) {
            this.issueType = issueType;
            return this;
        }

        public JobConfigEntryBuilder withStoreCacheJobName(String storeCacheJobName) {
            this.storeCacheJobName = storeCacheJobName;
            return this;
        }

        public JobConfigEntryBuilder withConfigs(List<AbstractFields> configs) {
            this.configs = configs;
            return this;
        }

        public JobConfigEntryBuilder withAutoRaiseIssues(boolean autoRaiseIssues) {
            this.autoRaiseIssue = autoRaiseIssues;
            return this;
        }

        public JobConfigEntryBuilder withOverrideResolvedIssues(boolean overrideResolvedIssues) {
            this.overrideResolvedIssues = overrideResolvedIssues;
            return this;
        }

        public JobConfigEntryBuilder withAutoResolveIssues(boolean autoResolveIssue) {
            this.autoResolveIssue = autoResolveIssue;
            return this;
        }

        public JobConfigEntryBuilder withAutoUnlinkIssues(boolean autoUnlinkIssues) {
            this.autoUnlinkIssue = autoUnlinkIssues;
            return this;
        }

        public JobConfigEntry build() {
            if(projectKey == null) { throw new IllegalStateException("The Project Key may not be null"); }
            if(issueType == null) { throw new IllegalStateException("The Issue Type may not be null"); }
            if(storeCacheJobName == null) { storeCacheJobName = "DEFAULT"; }
            StringFields summary = null;
            StringFields description = null;

            for(AbstractFields field : this.getConfigs()) {
                if(field instanceof StringFields) {
                    StringFields stringField = (StringFields) field;
                    if(stringField.getFieldKey().equals(SUMMARY_FIELD_NAME)) {
                        summary = stringField;
                    }
                    if(stringField.getFieldKey().equals(DESCRIPTION_FIELD_NAME)) {
                        description = stringField;
                    }
                }
            }

            if(summary == null) {
                this.getConfigs().add(DEFAULT_SUMMARY_FIELD);
            }

            if(description == null) {
                this.getConfigs().add(DEFAULT_DESCRIPTION_FIELD);
            }

            return this;
        }
    }

    private static JobConfigMapping instance = new JobConfigMapping();
    private static final String CONFIGS_FILE = "JiraIssueJobConfigs";

    /**
     * Getter for the singleton instance
     * @return
     */
    public static JobConfigMapping getInstance() {
        return instance;
    }
    private HashMap<String, JobConfigEntry> configMap;

    /**
     * Constructor. Will deserialize the existing map, or will create an empty new one
     */
    private JobConfigMapping(){
        configMap = new HashMap<String, JobConfigEntry>();

        for(Job project : Jenkins.getInstance().getItems(Job.class)) {
            JobConfigEntry entry = load(project);
            if (entry != null) {
                configMap.put(project.getFullName(), entry);
            }
        }
    }

    /**
     * Constructs the path for the config file
     * @param project
     * @return
     */
    private String getPathToFile(Job project) {
        return project.getRootDir().toPath().resolve(CONFIGS_FILE).toString();
    }

    private String getPathToJsonFile(Job project) {
        return project.getRootDir().toPath().resolve(CONFIGS_FILE).toString() + ".json";
    }

    /**
     * Looks for configurations from a previous version of the plugin and tries to load them
     * and save them in the new format
     * @param project
     * @return the loaded JobConfigEntry, or null if there was no file, or it could not be loaded
     */
    private JobConfigEntry loadBackwardsCompatible(Job project) {
        try {
            FileInputStream fileIn = new FileInputStream(getPathToFile(project));
            ObjectInputStream in = new ObjectInputStream(fileIn);
            JobConfigEntry entry = (JobConfigEntry) in.readObject();
            in.close();
            fileIn.close();
            JiraUtils.log("Found and successfully loaded configs from a previous version for job: "
                    + project.getFullName());
            //make sure we have the configurations from previous versions in the new format
            save(project, entry);
            return entry;
        } catch (FileNotFoundException e) {
           //Nothing to do
        } catch (Exception e) {
            JiraUtils.logError("ERROR: Found configs from a previous version, but was unable to load them for project "
                    + project.getFullName(), e);
        }
        return null;
    }

    /**
     * Loads the JobConfigEntry from the file associated with the project
     * @param project
     * @return the loaded JobConfigEntry, or null if there was no file, or it could not be loaded
     */
    private JobConfigEntry load(Job project) {
        JobConfigEntry entry = null;
        try{
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(AbstractFields.class, new FieldConfigsJsonAdapter())
                    .create();
            FileInputStream fileIn = new FileInputStream(getPathToJsonFile(project));
            JsonReader reader = new JsonReader(new InputStreamReader(fileIn, "UTF-8"));

            entry = gson.fromJson(reader, JobConfigEntry.class);
            reader.close();
            fileIn.close();

            return (JobConfigEntry) entry.readResolve();
        } catch (FileNotFoundException e) {
            entry = loadBackwardsCompatible(project);
            if(entry == null) {
                JiraUtils.log("No configs found for project " + project.getFullName());
            }
        } catch (Exception e) {
            JiraUtils.logError("ERROR: Could not load configs for project " + project.getFullName(), e);
        }
        return entry;
    }

    /**
     * Method for saving the map, called every time the map changes
     */
    private void save(Job project, JobConfigEntry entry) {
        try {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(AbstractFields.class, new FieldConfigsJsonAdapter())
                    .create();
            FileOutputStream fileOut = new FileOutputStream(getPathToJsonFile(project));
            JsonWriter writer = new JsonWriter(new OutputStreamWriter(fileOut, "UTF-8"));
            writer.setIndent("  ");
            gson.toJson(entry, JobConfigEntry.class, writer);
            writer.close();
            fileOut.close();
        }
        catch (Exception e) {
            JiraUtils.logError("ERROR: Could not save project map", e);
        }
    }

    /**
     * Method for setting the last configuration made for a project
     * @param project
     * @param projectKey
     * @param storeCacheJobName
     * @param issueType
     * @param configs
     */
    public synchronized void saveConfig(Job project,
                                        String projectKey,
                                        Long issueType,
                                        String storeCacheJobName,
                                        List<AbstractFields> configs,
                                        boolean autoRaiseIssue,
                                        boolean autoResolveIssue,
                                        boolean autoUnlinkIssue,
                                        boolean overrideResolvedIssues) {
        JobConfigEntry entry = new JobConfigEntry(projectKey, issueType, configs, autoRaiseIssue, autoResolveIssue, autoUnlinkIssue, overrideResolvedIssues);
        saveConfig(project, entry);
    }

    /**
     * Method for setting the last configuration made for a project
     */
    public synchronized void saveConfig(Job project, JobConfigEntry entry) {
        if(entry instanceof JobConfigEntryBuilder) {
            entry = ((JobConfigEntryBuilder) entry).build();
        }
        configMap.put(project.getFullName(), entry);
        save(project, entry);
    }

    private JobConfigEntry getJobConfigEntry(Job project) {
        if(!configMap.containsKey(project.getFullName())) {
            JobConfigEntry entry = load(project);
            if(entry != null) {
                configMap.put(project.getFullName(), entry);
            }
        }
        return configMap.get(project.getFullName());
    }

    /**
     * Getter for the last configured fields
     * @param project
     * @return
     */
    public List<AbstractFields> getConfig(Job project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getConfigs() : null;
    }

    /**
     * Getter for the last configured issue type
     * @param project
     * @return
     */
    public Long getIssueType(Job project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getIssueType() : null;
    }

    /**
     * Getter for the last configured project key
     * @param project
     * @return
     */
    public String getProjectKey(Job project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getProjectKey() : null;
    }

    /**
     * Getter for the last configured store cache job name
     * @param project
     * @return
     */
    public String getStoreCacheJobName(Job project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getStoreCacheJobName() : null;
    }

    public boolean getAutoRaiseIssue(Job project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getAutoRaiseIssue() : false;
    }

    public boolean getOverrideResolvedIssues(Job project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getOverrideResolvedIssues() : false;
    }

    public boolean getAutoResolveIssue(Job project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getAutoResolveIssue() : false;
    }

    public boolean getAutoUnlinkIssue(Job project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getAutoUnlinkIssue() : false;
    }

    /**
     * Getter for the issue key pattern, used to validate user input
     * @param project
     * @return
     */
    public Pattern getIssueKeyPattern(Job project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getIssueKeyPattern() : null;
    }
}
