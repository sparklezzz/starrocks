// This file is made available under Elastic License 2.0.
// This file is based on code available under the Apache license here:
//   https://github.com/apache/incubator-doris/blob/master/fe/fe-core/src/main/java/org/apache/doris/load/DppConfig.java

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.starrocks.load;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.starrocks.common.FeConstants;
import com.starrocks.common.FeMetaVersion;
import com.starrocks.common.LoadException;
import com.starrocks.common.io.Text;
import com.starrocks.common.io.Writable;
import com.starrocks.server.GlobalStateMgr;
import com.starrocks.sql.ast.LoadStmt;
import com.starrocks.thrift.TPriority;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

@Deprecated
public class DppConfig implements Writable {
    // necessary hadoop job config keys
    private static final String FS_DEFAULT_NAME = "fs.default.name";
    private static final String MAPRED_JOB_TRACKER = "mapred.job.tracker";
    private static final String HADOOP_JOB_UGI = "hadoop.job.ugi";

    // bos hadoop keys
    private static final String FS_BOS_ENDPOINT = "fs.bos.endpoint";
    private static final String FS_BOS_ACCESS_KEY = "fs.bos.access.key";
    private static final String FS_BOS_SECRET_ACCESS_KEY = "fs.bos.secret.access.key";

    private static final String SEMICOLON_SEPARATOR = ";";
    private static final String EQUAL_SEPARATOR = "=";
    private static final String COLON_SEPARATOR = ":";

    private static final String APPLICATIONS_PATH = "applications";
    private static final String OUTPUT_PATH = "output";

    public static final String STARROCKS_PATH = "hadoop_starrocks_path";
    public static final String HTTP_PORT = "hadoop_http_port";
    public static final String HADOOP_CONFIGS = "hadoop_configs";
    public static final String PRIORITY = "priority";

    public static final String CLUSTER_NAME_REGEX = "[a-z][a-z0-9-_]{0,63}";

    private static final int DEFAULT_HTTP_PORT = 8070;

    // starrocks base path in hadoop
    //   dpp: starrocksPath/cluster_id/applications/dpp_version
    //   output: starrocksPath/cluster_id/output
    private String starrocksPath;
    private int httpPort;
    private Map<String, String> hadoopConfigs;

    // priority for starrocks internal schedule
    // for now are etl submit schedule and download file schedule
    private TPriority priority;

    // for persist
    public DppConfig() {
        this(null, -1, null, null);
    }

    private DppConfig(String starrocksPath, int httpPort, Map<String, String> hadoopConfigs, TPriority priority) {
        this.starrocksPath = starrocksPath;
        this.httpPort = httpPort;
        this.hadoopConfigs = hadoopConfigs;
        this.priority = priority;
    }

    public static DppConfig create(Map<String, String> configMap) throws LoadException {
        String starrocksPath = null;
        int httpPort = -1;
        Map<String, String> hadoopConfigs = Maps.newHashMap();
        TPriority priority = null;

        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.equalsIgnoreCase(STARROCKS_PATH)) {
                // starrocks path
                if (Strings.isNullOrEmpty(value)) {
                    throw new LoadException("Load cluster " + STARROCKS_PATH + " is null");
                }
                starrocksPath = value.trim();
            } else if (key.equalsIgnoreCase(HTTP_PORT)) {
                // http port
                try {
                    httpPort = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new LoadException("Load cluster " + HTTP_PORT + " is not INT");
                }
            } else if (key.equalsIgnoreCase(HADOOP_CONFIGS)) {
                // hadoop configs
                if (Strings.isNullOrEmpty(value)) {
                    throw new LoadException("Load cluster " + HADOOP_CONFIGS + " is null");
                }

                value = value.trim();
                for (String config : value.split(SEMICOLON_SEPARATOR)) {
                    config = config.trim();
                    if (config.equals("")) {
                        continue;
                    }

                    String[] keyValueArr = config.split(EQUAL_SEPARATOR);
                    if (keyValueArr.length != 2) {
                        throw new LoadException("Load cluster " + HADOOP_CONFIGS + " format error");
                    }

                    hadoopConfigs.put(keyValueArr[0], keyValueArr[1]);
                }
            } else if (key.equalsIgnoreCase(PRIORITY)) {
                try {
                    priority = TPriority.valueOf(value);
                } catch (Exception e) {
                    throw new LoadException("Load cluster " + PRIORITY + " format error");
                }
            } else {
                throw new LoadException("Unknown load cluster config key: " + key);
            }
        }

        if (hadoopConfigs.isEmpty()) {
            hadoopConfigs = null;
        }

        return new DppConfig(starrocksPath, httpPort, hadoopConfigs, priority);
    }

    public void update(DppConfig dppConfig) {
        update(dppConfig, false);
    }

    public void update(DppConfig dppConfig, boolean needReplace) {
        if (dppConfig == null) {
            return;
        }

        if (dppConfig.starrocksPath != null) {
            starrocksPath = dppConfig.starrocksPath;
        }

        if (dppConfig.httpPort != -1) {
            httpPort = dppConfig.httpPort;
        }

        if (dppConfig.hadoopConfigs != null) {
            if (needReplace) {
                if (!dppConfig.hadoopConfigs.isEmpty()) {
                    hadoopConfigs = dppConfig.hadoopConfigs;
                }
            } else {
                if (hadoopConfigs == null) {
                    hadoopConfigs = Maps.newHashMap();
                }
                hadoopConfigs.putAll(dppConfig.hadoopConfigs);
            }
        }

        if (dppConfig.priority != null) {
            priority = dppConfig.priority;
        }
    }

    public void updateHadoopConfigs(Map<String, String> configMap) throws LoadException {
        if (configMap == null) {
            return;
        }

        if (hadoopConfigs == null) {
            hadoopConfigs = Maps.newHashMap();
        }

        // bos configs
        int bosParameters = 0;
        if (configMap.containsKey(LoadStmt.BOS_ENDPOINT)) {
            String bosEndpoint = configMap.get(LoadStmt.BOS_ENDPOINT);
            hadoopConfigs.put(FS_BOS_ENDPOINT, bosEndpoint);
        }

        if (configMap.containsKey(LoadStmt.BOS_ACCESSKEY)) {
            bosParameters++;
            String bosAccessKey = configMap.get(LoadStmt.BOS_ACCESSKEY);
            hadoopConfigs.put(FS_BOS_ACCESS_KEY, bosAccessKey);
        }

        if (configMap.containsKey(LoadStmt.BOS_SECRET_ACCESSKEY)) {
            bosParameters++;
            String bosSecretAccessKey = configMap.get(LoadStmt.BOS_SECRET_ACCESSKEY);
            hadoopConfigs.put(FS_BOS_SECRET_ACCESS_KEY, bosSecretAccessKey);
        }

        if (bosParameters > 0 && bosParameters < 2) {
            throw new LoadException("You should specify 3 parameters (" + LoadStmt.BOS_ENDPOINT + ", "
                    + LoadStmt.BOS_ACCESSKEY + ", " + LoadStmt.BOS_SECRET_ACCESSKEY
                    + ") when loading data from BOS");
        }

        if (hadoopConfigs.isEmpty()) {
            hadoopConfigs = null;
        }
    }

    public void resetConfigByKey(String key) throws LoadException {
        if (key.equalsIgnoreCase(STARROCKS_PATH)) {
            starrocksPath = null;
        } else if (key.equalsIgnoreCase(HTTP_PORT)) {
            httpPort = DEFAULT_HTTP_PORT;
        } else if (key.equalsIgnoreCase(HADOOP_CONFIGS)) {
            hadoopConfigs = null;
        } else if (key.equalsIgnoreCase(PRIORITY)) {
            priority = TPriority.NORMAL;
        } else {
            throw new LoadException("Unknown load cluster config key: " + key);
        }
    }

    public void clear() {
        // retain 3 necessary configs for clear dpp output
        Iterator<Map.Entry<String, String>> iter = hadoopConfigs.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, String> entry = iter.next();
            String key = entry.getKey();
            if (!key.equals(FS_DEFAULT_NAME) && !key.equals(MAPRED_JOB_TRACKER) && !key.equals(HADOOP_JOB_UGI)) {
                iter.remove();
            }
        }
    }

    public void check() throws LoadException {
        if (Strings.isNullOrEmpty(starrocksPath)) {
            throw new LoadException("Load cluster " + STARROCKS_PATH + " is null");
        }

        if (httpPort == -1) {
            // use default
            httpPort = DEFAULT_HTTP_PORT;
        }

        // check necessary hadoop configs
        if (hadoopConfigs == null) {
            throw new LoadException("Load cluster " + HADOOP_CONFIGS + " is null");
        }
        if (!hadoopConfigs.containsKey(FS_DEFAULT_NAME)) {
            throw new LoadException("Load cluster " + FS_DEFAULT_NAME + " not set");
        }
        if (!hadoopConfigs.containsKey(MAPRED_JOB_TRACKER)) {
            throw new LoadException("Load cluster " + MAPRED_JOB_TRACKER + " not set");
        }
        if (!hadoopConfigs.containsKey(HADOOP_JOB_UGI)) {
            throw new LoadException("Load cluster " + HADOOP_JOB_UGI + " not set");
        }
    }

    public DppConfig getCopiedDppConfig() {
        Map<String, String> copiedHadoopConfigs = null;
        if (hadoopConfigs != null) {
            copiedHadoopConfigs = Maps.newHashMap(hadoopConfigs);
        }

        return new DppConfig(starrocksPath, httpPort, copiedHadoopConfigs, priority);
    }

    public static String getStarRocksPathKey() {
        return STARROCKS_PATH;
    }

    public String getStarRocksPath() {
        return starrocksPath;
    }

    public String getApplicationsPath() {
        return String.format("%s/%d/%s/%s", starrocksPath, GlobalStateMgr.getCurrentState().getClusterId(),
                APPLICATIONS_PATH,
                FeConstants.dpp_version);
    }

    public String getOutputPath() {
        return String.format("%s/%d/%s", starrocksPath, GlobalStateMgr.getCurrentState().getClusterId(), OUTPUT_PATH);
    }

    public static String getHttpPortKey() {
        return HTTP_PORT;
    }

    public int getHttpPort() {
        if (httpPort == -1) {
            return DEFAULT_HTTP_PORT;
        } else {
            return httpPort;
        }
    }

    public String getFsDefaultName() {
        return hadoopConfigs.get(FS_DEFAULT_NAME);
    }

    public String getNameNodeHost() {
        // hdfs://host:port
        String fsDefaultName = hadoopConfigs.get(FS_DEFAULT_NAME);
        String[] arr = fsDefaultName.split(COLON_SEPARATOR);
        if (arr.length != 3) {
            return null;
        }

        return arr[1].substring(2);
    }

    public String getHadoopJobUgiStr() {
        return hadoopConfigs.get(HADOOP_JOB_UGI);
    }

    public static String getHadoopConfigsKey() {
        return HADOOP_CONFIGS;
    }

    public Map<String, String> getHadoopConfigs() {
        return hadoopConfigs;
    }

    public static String getPriorityKey() {
        return PRIORITY;
    }

    public TPriority getPriority() {
        if (priority == null) {
            return TPriority.NORMAL;
        }

        return priority;
    }

    @Override
    public String toString() {
        return "DppConfig{starrocksPath=" + starrocksPath + ", httpPort=" + httpPort + ", hadoopConfigs=" +
                hadoopConfigs + "}";
    }

    @Override
    public void write(DataOutput out) throws IOException {
        if (starrocksPath != null) {
            out.writeBoolean(true);
            Text.writeString(out, starrocksPath);
        } else {
            out.writeBoolean(false);
        }

        out.writeInt(httpPort);

        if (hadoopConfigs != null) {
            out.writeBoolean(true);
            out.writeInt(hadoopConfigs.size());
            for (Map.Entry<String, String> entry : hadoopConfigs.entrySet()) {
                Text.writeString(out, entry.getKey());
                Text.writeString(out, entry.getValue());
            }
        } else {
            out.writeBoolean(false);
        }

        if (priority == null) {
            priority = TPriority.NORMAL;
        }
        Text.writeString(out, priority.name());
    }

    public void readFields(DataInput in) throws IOException {
        boolean readStarRocksPath = false;
        if (GlobalStateMgr.getCurrentStateJournalVersion() >= FeMetaVersion.VERSION_12) {
            if (in.readBoolean()) {
                readStarRocksPath = true;
            }
        } else {
            readStarRocksPath = true;
        }
        if (readStarRocksPath) {
            starrocksPath = Text.readString(in);
        }

        httpPort = in.readInt();

        boolean readHadoopConfigs = false;
        if (GlobalStateMgr.getCurrentStateJournalVersion() >= FeMetaVersion.VERSION_12) {
            if (in.readBoolean()) {
                readHadoopConfigs = true;
            }
        } else {
            readHadoopConfigs = true;
        }
        if (readHadoopConfigs) {
            hadoopConfigs = Maps.newHashMap();
            int count = in.readInt();
            for (int i = 0; i < count; ++i) {
                hadoopConfigs.put(Text.readString(in), Text.readString(in));
            }
        }

        if (GlobalStateMgr.getCurrentStateJournalVersion() >= FeMetaVersion.VERSION_15) {
            this.priority = TPriority.valueOf(Text.readString(in));
        } else {
            this.priority = TPriority.NORMAL;
        }
    }

}
