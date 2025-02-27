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

package com.starrocks.broker.hdfs;

import com.google.common.base.Stopwatch;
import com.starrocks.common.BrokerPerfMonitor;
import com.starrocks.thrift.TBrokerCheckPathExistRequest;
import com.starrocks.thrift.TBrokerCheckPathExistResponse;
import com.starrocks.thrift.TBrokerCloseReaderRequest;
import com.starrocks.thrift.TBrokerCloseWriterRequest;
import com.starrocks.thrift.TBrokerDeletePathRequest;
import com.starrocks.thrift.TBrokerFD;
import com.starrocks.thrift.TBrokerFileStatus;
import com.starrocks.thrift.TBrokerListPathRequest;
import com.starrocks.thrift.TBrokerListResponse;
import com.starrocks.thrift.TBrokerOpenReaderRequest;
import com.starrocks.thrift.TBrokerOpenReaderResponse;
import com.starrocks.thrift.TBrokerOpenWriterRequest;
import com.starrocks.thrift.TBrokerOpenWriterResponse;
import com.starrocks.thrift.TBrokerOperationStatus;
import com.starrocks.thrift.TBrokerOperationStatusCode;
import com.starrocks.thrift.TBrokerPReadRequest;
import com.starrocks.thrift.TBrokerPWriteRequest;
import com.starrocks.thrift.TBrokerPingBrokerRequest;
import com.starrocks.thrift.TBrokerReadResponse;
import com.starrocks.thrift.TBrokerRenamePathRequest;
import com.starrocks.thrift.TBrokerSeekRequest;
import com.starrocks.thrift.TFileBrokerService;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HDFSBrokerServiceImpl implements TFileBrokerService.Iface {

    private static Logger logger = Logger.getLogger(HDFSBrokerServiceImpl.class.getName());
    private FileSystemManager fileSystemManager;

    public HDFSBrokerServiceImpl() {
        fileSystemManager = new FileSystemManager();
    }

    private TBrokerOperationStatus generateOKStatus() {
        return new TBrokerOperationStatus(TBrokerOperationStatusCode.OK);
    }

    @Override
    public TBrokerListResponse listPath(TBrokerListPathRequest request)
            throws TException {
        logger.info("receive a delete path request, path: " + request.path);
        TBrokerListResponse response = new TBrokerListResponse();
        try {
            boolean fileNameOnly = false;
            if (request.isSetFileNameOnly()) {
                fileNameOnly = request.isFileNameOnly();
            }
            List<TBrokerFileStatus> fileStatuses = fileSystemManager.listPath(request.path, fileNameOnly,
                    request.properties);
            response.setOpStatus(generateOKStatus());
            response.setFiles(fileStatuses);
            return response;
        } catch (BrokerException e) {
            logger.warn("failed to list path: " + request.path, e);
            TBrokerOperationStatus errorStatus = e.generateFailedOperationStatus();
            response.setOpStatus(errorStatus);
            return response;
        }
    }

    @Override
    public TBrokerOperationStatus deletePath(TBrokerDeletePathRequest request)
            throws TException {
        logger.info("receive a delete path request, path: " + request.path);
        try {
            fileSystemManager.deletePath(request.path, request.properties);
        } catch (BrokerException e) {
            logger.warn("failed to delete path: " + request.path, e);
            TBrokerOperationStatus errorStatus = e.generateFailedOperationStatus();
            return errorStatus;
        }
        return generateOKStatus();
    }

    @Override
    public TBrokerOperationStatus renamePath(TBrokerRenamePathRequest request)
            throws TException {
        logger.info("receive a rename path request, source path: " + request.srcPath + ", dest path: " + request.destPath);
        try {
            fileSystemManager.renamePath(request.srcPath, request.destPath, request.properties);
        } catch (BrokerException e) {
            logger.warn("failed to rename path: " + request.srcPath + " to " + request.destPath, e);
            TBrokerOperationStatus errorStatus = e.generateFailedOperationStatus();
            return errorStatus;
        }
        return generateOKStatus();
    }

    @Override
    public TBrokerCheckPathExistResponse checkPathExist(
            TBrokerCheckPathExistRequest request) throws TException {
        logger.info("receive a check path request, path: " + request.path);
        TBrokerCheckPathExistResponse response = new TBrokerCheckPathExistResponse();
        try {
            boolean isPathExist = fileSystemManager.checkPathExist(request.path, request.properties);
            response.setIsPathExist(isPathExist);
            response.setOpStatus(generateOKStatus());
        } catch (BrokerException e) {
            logger.warn("failed to check path exist: " + request.path, e);
            TBrokerOperationStatus errorStatus = e.generateFailedOperationStatus();
            response.setOpStatus(errorStatus);
        }
        return response;
    }

    @Override
    public TBrokerOpenReaderResponse openReader(TBrokerOpenReaderRequest request)
            throws TException {
        logger.info("receive a open reader request, path: " + request.path + ", start offset: " + request.startOffset + ", client id: " + request.clientId);
        TBrokerOpenReaderResponse response = new TBrokerOpenReaderResponse();
        try {
            TBrokerFD fd = fileSystemManager.openReader(request.clientId, request.path,
                    request.startOffset, request.properties);
            response.setFd(fd);
            response.setOpStatus(generateOKStatus());
        } catch (BrokerException e) {
            logger.warn("failed to open reader for path: " + request.path, e);
            TBrokerOperationStatus errorStatus = e.generateFailedOperationStatus();
            response.setOpStatus(errorStatus);
        }
        return response;
    }

    @Override
    public TBrokerReadResponse pread(TBrokerPReadRequest request)
            throws TException {
        logger.debug("receive a read request, fd: " + request.fd + ", offset: " + request.offset + ", length: " + request.length);
        Stopwatch stopwatch = BrokerPerfMonitor.startWatch();
        TBrokerReadResponse response = new TBrokerReadResponse();
        try {
            ByteBuffer readBuf = fileSystemManager.pread(request.fd, request.offset, request.length);
            response.setData(readBuf);
            response.setOpStatus(generateOKStatus());
        } catch (BrokerException e) {
            logger.warn("failed to pread: " + request.fd, e);
            TBrokerOperationStatus errorStatus = e.generateFailedOperationStatus();
            response.setOpStatus(errorStatus);
            return response;
        } finally {
            stopwatch.stop();
            logger.debug("read request fd: " + request.fd.high + ""
                    + request.fd.low + " cost "
                    + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " millis");
        }
        return response;
    }

    @Override
    public TBrokerOperationStatus seek(TBrokerSeekRequest request)
            throws TException {
        logger.debug("receive a seek request, fd: " + request.fd + ", offset: " + request.offset);
        try {
            fileSystemManager.seek(request.fd, request.offset);
        } catch (BrokerException e) {
            logger.warn("failed to seek: " + request.fd, e);
            TBrokerOperationStatus errorStatus = e.generateFailedOperationStatus();
            return errorStatus;
        }
        return generateOKStatus();
    }

    @Override
    public TBrokerOperationStatus closeReader(TBrokerCloseReaderRequest request)
            throws TException {
        logger.info("receive a close reader request, fd: " + request.fd);

        try {
            fileSystemManager.closeReader(request.fd);
        } catch (BrokerException e) {
            logger.warn("failed to close reader: " + request.fd, e);
            TBrokerOperationStatus errorStatus = e.generateFailedOperationStatus();
            return errorStatus;
        }
        return generateOKStatus();
    }

    @Override
    public TBrokerOpenWriterResponse openWriter(TBrokerOpenWriterRequest request)
            throws TException {
        logger.info("receive a open writer request, path: " + request.path + ", mode: " + request.openMode + ", client id: " + request.clientId);
        TBrokerOpenWriterResponse response = new TBrokerOpenWriterResponse();
        try {
            TBrokerFD fd = fileSystemManager.openWriter(request.clientId, request.path, request.properties);
            response.setFd(fd);
            logger.info("finish a open writer request. fd: " + fd + ", request: " + request);
            response.setOpStatus(generateOKStatus());
        } catch (BrokerException e) {
            logger.warn("failed to open writer: " + request.path, e);
            TBrokerOperationStatus errorStatus = e.generateFailedOperationStatus();
            response.setOpStatus(errorStatus);
        }
        return response;
    }

    @Override
    public TBrokerOperationStatus pwrite(TBrokerPWriteRequest request)
            throws TException {
        logger.debug("receive a pwrite request, fd: " + request.fd + ", offset: " + request.offset + ", size: " + request.data.remaining());
        Stopwatch stopwatch = BrokerPerfMonitor.startWatch();
        try {
            fileSystemManager.pwrite(request.fd, request.offset, request.getData());
        } catch (BrokerException e) {
            logger.warn("failed to pwrite: " + request.fd, e);
            TBrokerOperationStatus errorStatus = e.generateFailedOperationStatus();
            return errorStatus;
        } finally {
            stopwatch.stop();
            logger.debug("write request fd: " + request.fd.high + ""
                    + request.fd.low + " cost "
                    + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " millis");
        }
        return generateOKStatus();
    }

    @Override
    public TBrokerOperationStatus closeWriter(TBrokerCloseWriterRequest request)
            throws TException {
        logger.info("receive a close writer request, request detail: " + request);
        try {
            fileSystemManager.closeWriter(request.fd);
        } catch (BrokerException e) {
            logger.warn("failed to close writer: " + request.fd, e);
            TBrokerOperationStatus errorStatus = e.generateFailedOperationStatus();
            return errorStatus;
        }
        return generateOKStatus();
    }

    @Override
    public TBrokerOperationStatus ping(TBrokerPingBrokerRequest request)
            throws TException {
        logger.debug("receive a ping request, client id: " + request.clientId);
        try {
            fileSystemManager.ping(request.clientId);
        } catch (BrokerException e) {
            logger.warn("failed to ping: ", e);
            TBrokerOperationStatus errorStatus = e.generateFailedOperationStatus();
            return errorStatus;
        }
        return generateOKStatus();
    }
}
