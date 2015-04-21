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
package nl.esciencecenter.xenon.adaptors.ftp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import nl.esciencecenter.xenon.XenonException;
import nl.esciencecenter.xenon.XenonPropertyDescription.Component;
import nl.esciencecenter.xenon.credentials.Credential;
import nl.esciencecenter.xenon.engine.XenonEngine;
import nl.esciencecenter.xenon.engine.XenonProperties;
import nl.esciencecenter.xenon.engine.credentials.PasswordCredentialImplementation;
import nl.esciencecenter.xenon.engine.files.FileSystemImplementation;
import nl.esciencecenter.xenon.engine.files.PathImplementation;
import nl.esciencecenter.xenon.files.Copy;
import nl.esciencecenter.xenon.files.CopyOption;
import nl.esciencecenter.xenon.files.CopyStatus;
import nl.esciencecenter.xenon.files.DirectoryStream;
import nl.esciencecenter.xenon.files.DirectoryStream.Filter;
import nl.esciencecenter.xenon.files.FileAttributes;
import nl.esciencecenter.xenon.files.FileSystem;
import nl.esciencecenter.xenon.files.Files;
import nl.esciencecenter.xenon.files.OpenOption;
import nl.esciencecenter.xenon.files.Path;
import nl.esciencecenter.xenon.files.PathAttributesPair;
import nl.esciencecenter.xenon.files.PosixFilePermission;
import nl.esciencecenter.xenon.files.RelativePath;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtpFiles implements Files {

    private static final Logger LOGGER = LoggerFactory.getLogger(FtpFiles.class);

    private static int currentID = 1;

    private static synchronized String getNewUniqueID() {
        String res = "ftp" + currentID;
        currentID++;
        return res;
    }

    /**
     * Used to store all state attached to a filesystem. This way, FileSystemImplementation is immutable.
     */
    static class FileSystemInfo {

        private final FileSystemImplementation impl;
        private final FTPClient ftpClient;

        public FileSystemInfo(FileSystemImplementation impl, FTPClient ftpClient) {
            super();
            this.impl = impl;
            this.ftpClient = ftpClient;
        }

        public FileSystemImplementation getImpl() {
            return impl;
        }

        public FTPClient getFtpClient() {
            return ftpClient;
        }
    }

    private final XenonEngine xenonEngine;
    private final FtpAdaptor adaptor;

    private Map<String, FileSystemInfo> fileSystems = Collections.synchronizedMap(new HashMap<String, FileSystemInfo>());

    public FtpFiles(FtpAdaptor ftpAdaptor, XenonEngine xenonEngine) {
        this.xenonEngine = xenonEngine;
        adaptor = ftpAdaptor;
    }

    @Override
    public FileSystem newFileSystem(String scheme, String location, Credential credential, Map<String, String> properties)
            throws XenonException {
        LOGGER.debug("newFileSystem scheme = {} location = {} credential = {} properties = {}", scheme, location, credential,
                properties);

        if (credential == null) {
            throw new XenonException(adaptor.getName(), "Credentials was null.");
        }

        XenonProperties xenonProperties = new XenonProperties(adaptor.getSupportedProperties(Component.FILESYSTEM), properties);

        FtpLocation ftpLocation = FtpLocation.parse(location);
        FTPClient ftpClient = new FTPClient();
        connectToServer(ftpLocation, ftpClient);
        login(credential, ftpClient);
        String cwd = getCurrentWorkingDirectory(ftpClient);

        RelativePath entryPath = new RelativePath(cwd);
        String uniqueID = getNewUniqueID();
        FileSystemImplementation fileSystem = new FileSystemImplementation(FtpAdaptor.ADAPTOR_NAME, uniqueID, scheme, location,
                entryPath, credential, xenonProperties);
        fileSystems.put(uniqueID, new FileSystemInfo(fileSystem, ftpClient));

        LOGGER.debug("* newFileSystem OK remote cwd = {} entryPath = {} uniqueID = {}", cwd, entryPath, uniqueID);
        return fileSystem;
    }

    private String getCurrentWorkingDirectory(FTPClient ftpClient) throws XenonException {
        String wd = null;
        try {
            wd = ftpClient.printWorkingDirectory();
        } catch (IOException e) {
            throw new XenonException(adaptor.getName(), "Could not retrieve current working directory", e);
        }
        return wd;
    }

    private void connectToServer(FtpLocation ftpLocation, FTPClient ftp) throws XenonException {
        try {
            ftp.connect(ftpLocation.getHost(), ftpLocation.getPort());
        } catch (IOException e) {
            throw new XenonException(adaptor.getName(), "Failed to connect", e);
        }
    }

    private void login(Credential credential, FTPClient ftp) throws XenonException {
        try {
            loginWithCredentialOrDefault(ftp, credential);
            int replyCode = ftp.getReplyCode();
            verifySuccessByServerCode(replyCode);
        } catch (XenonException | IOException e) {
            throw new XenonException(adaptor.getName(), "Failed to login", e);
        }
    }

    private void verifySuccessByServerCode(int replyCode) throws XenonException {
        if ((replyCode >= 200 && replyCode < 300) == false) {
            throw new XenonException(adaptor.getName(), "Server status not succesfull (status code " + replyCode + ").");
        }
    }

    private void loginWithCredentialOrDefault(FTPClient ftp, Credential credential) throws IOException {
        String password = "";
        String user = "anonymous";
        if (credential instanceof PasswordCredentialImplementation) {
            PasswordCredentialImplementation passwordCredential = (PasswordCredentialImplementation) credential;
            password = new String(passwordCredential.getPassword());
            user = passwordCredential.getUsername();
        }
        System.out.println("Logging in as '" + user + "' : '" + password + "'."); // TODO remove this debugging line
        ftp.login(user, password);
    }

    @Override
    public Path newPath(FileSystem filesystem, RelativePath location) throws XenonException {
        return new PathImplementation(filesystem, location);
    }

    @Override
    public void close(FileSystem fileSystem) throws XenonException {
        LOGGER.debug("close fileSystem = {}", fileSystem);

        if (isOpen(fileSystem) == false) {
            throw new XenonException(adaptor.getName(), "file system is already closed");
        }

        FileSystemImplementation fs = (FileSystemImplementation) fileSystem;
        FileSystemInfo info = fileSystems.remove(fs.getUniqueID());

        try {
            info.getFtpClient().disconnect();
        } catch (IOException e) {
            throw new XenonException(adaptor.getName(), "Exception while disconnecting ftp file system.");
        }

        LOGGER.debug("close OK");
    }

    @Override
    public boolean isOpen(FileSystem fileSystem) throws XenonException {
        LOGGER.debug("isOpen fileSystem = {}", fileSystem);

        FileSystemImplementation fs = (FileSystemImplementation) fileSystem;
        FileSystemInfo fileSystemInfo = fileSystems.get(fs.getUniqueID());
        boolean result = (fileSystemInfo != null) && fileSystemInfo.getFtpClient().isConnected();

        LOGGER.debug("isOpen OK result = {}", result);

        return result;
    }

    @Override
    public Copy copy(Path source, Path target, CopyOption... options) throws XenonException {
        return null;
    }

    @Override
    public void move(Path source, Path target) throws XenonException {
    }

    @Override
    public CopyStatus getCopyStatus(Copy copy) throws XenonException {
        return null;
    }

    @Override
    public CopyStatus cancelCopy(Copy copy) throws XenonException {
        return null;
    }

    @Override
    public void createDirectories(Path path) throws XenonException {
        RelativePath relativeParent = path.getRelativePath().getParent();

        if (relativeParent != null) {
            PathImplementation parentPath = new PathImplementation(path.getFileSystem(), relativeParent);
            if (exists(parentPath) == false) {
                createDirectories(parentPath);
            }
        }
        createDirectory(path);
    }

    @Override
    public void createDirectory(Path path) throws XenonException {
        FtpCommand ftpCommand = new FtpCommand() {
            @Override
            public boolean execute(FTPClient ftpClient, String absolutePath) throws IOException {
                boolean result = ftpClient.makeDirectory(absolutePath);
                replyString = ftpClient.getReplyString();
                return result;

            }
        };
        String messageInCaseOfError = "Failed to create directory";

        executeFtpCommand(path, ftpCommand, messageInCaseOfError);
    }

    private void executeFtpCommand(Path path, FtpCommand ftpCommand, String messageInCaseOfError) throws XenonException {
        FileSystemImplementation fileSystem = (FileSystemImplementation) path.getFileSystem();
        FTPClient ftpClient = fileSystems.get(fileSystem.getUniqueID()).getFtpClient();
        String absolutePath = path.getRelativePath().getAbsolutePath();

        try {
            boolean hasSucceeded = ftpCommand.execute(ftpClient, absolutePath);
            if (hasSucceeded == false) {
                throw new IOException(ftpCommand.getReplyString());
            }
        } catch (IOException e) {
            throw new XenonException(adaptor.getName(), messageInCaseOfError + " " + absolutePath, e);
        }
    }

    @Override
    public void createFile(Path path) throws XenonException {
        FtpCommand ftpCommand = new FtpCommand() {
            @Override
            public boolean execute(FTPClient ftpClient, String absolutePath) throws IOException {
                InputStream dummyContent = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
                boolean result = ftpClient.storeFile(absolutePath, dummyContent);
                replyString = ftpClient.getReplyString();
                return result;
            }
        };
        String messageInCaseOfError = "Failed to create file";

        executeFtpCommand(path, ftpCommand, messageInCaseOfError);
    }

    @Override
    public void delete(Path path) throws XenonException {
        if (getAttributes(path).isDirectory()) {
            deleteDirectory(path);
        } else {
            deleteFile(path);
        }
        ;
    }

    private void deleteDirectory(Path path) throws XenonException {
        FtpCommand ftpCommand = new FtpCommand() {
            @Override
            public boolean execute(FTPClient ftpClient, String absolutePath) throws IOException {
                boolean result = ftpClient.removeDirectory(absolutePath);
                replyString = ftpClient.getReplyString();
                return result;
            }
        };
        String messageInCaseOfError = "Failed to delete file or directory";

        executeFtpCommand(path, ftpCommand, messageInCaseOfError);
    }

    private void deleteFile(Path path) throws XenonException {
        FtpCommand ftpCommand = new FtpCommand() {
            @Override
            public boolean execute(FTPClient ftpClient, String absolutePath) throws IOException {
                boolean result = ftpClient.deleteFile(absolutePath);
                replyString = ftpClient.getReplyString();
                return result;
            }
        };
        String messageInCaseOfError = "Failed to delete file or directory";

        executeFtpCommand(path, ftpCommand, messageInCaseOfError);
    }

    @Override
    public boolean exists(Path path) throws XenonException {
        FileSystemImplementation fileSystem = (FileSystemImplementation) path.getFileSystem();
        FTPClient ftpClient = fileSystems.get(fileSystem.getUniqueID()).getFtpClient();
        try {
            return fileExists(ftpClient, path) || directoryExists(ftpClient, path);
        } catch (IOException e) {
            String absolutePath = path.getRelativePath().getAbsolutePath();
            String message = MessageFormat.format("Failed to check whether path {0} exists.", absolutePath);
            throw new XenonException(adaptor.getName(), message, e);
        }
    }

    private boolean directoryExists(FTPClient ftpClient, Path path) throws IOException {
        String absolutePath = path.getRelativePath().getAbsolutePath();
        String originalWorkingDirectory = ftpClient.printWorkingDirectory();
        boolean pathExists = ftpClient.changeWorkingDirectory(absolutePath);
        ftpClient.changeWorkingDirectory(originalWorkingDirectory);
        return pathExists;
    }

    private boolean fileExists(FTPClient ftpClient, Path path) throws IOException {
        String absolutePath = path.getRelativePath().getAbsolutePath();
        return ftpClient.listFiles(absolutePath).length == 1;
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir) throws XenonException {
        return null;
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, Filter filter) throws XenonException {
        return null;
    }

    @Override
    public DirectoryStream<PathAttributesPair> newAttributesDirectoryStream(Path dir) throws XenonException {
        return null;
    }

    @Override
    public DirectoryStream<PathAttributesPair> newAttributesDirectoryStream(Path dir, Filter filter) throws XenonException {
        return null;
    }

    @Override
    public InputStream newInputStream(Path path) throws XenonException {
        return null;
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws XenonException {
        return null;
    }

    @Override
    public FileAttributes getAttributes(Path path) throws XenonException {
        if (exists(path) == false) {
            throw new XenonException(adaptor.getName(), MessageFormat.format("Path {0} does not exist!", path));
        }

        FileSystemImplementation fileSystem = (FileSystemImplementation) path.getFileSystem();
        FTPClient ftpClient = fileSystems.get(fileSystem.getUniqueID()).getFtpClient();

        FTPFile listFile = getFtpFile(ftpClient, path);

        return new FtpFileAttributes(listFile);
    }

    private FTPFile getFtpFile(FTPClient ftpClient, Path path) throws XenonException {
        FTPFile listFile;
        try {
            if (directoryExists(ftpClient, path)) {
                listFile = getDirectoryFtpFile(ftpClient, path);
            } else {
                listFile = getRegularFtpFile(ftpClient, path);
            }
        } catch (IOException e) {
            String absolutePath = path.getRelativePath().getAbsolutePath();
            String message = MessageFormat.format("Failed to retrieve attributes of path {0}", absolutePath);
            throw new XenonException(adaptor.getName(), message, e);
        }
        return listFile;
    }

    private FTPFile getRegularFtpFile(FTPClient ftpClient, Path pathToRegularFile) throws IOException {
        String absolutePath = pathToRegularFile.getRelativePath().getAbsolutePath();
        return ftpClient.listFiles(absolutePath)[0];
    }

    private FTPFile getDirectoryFtpFile(FTPClient ftpClient, Path path) throws IOException, XenonException {
        RelativePath parent = path.getRelativePath().getParent();

        if (parent == null) {
            String absolutePath = path.getRelativePath().getAbsolutePath();
            String message = MessageFormat.format("Cannot get attributes of root path {0}", absolutePath);
            throw new XenonException(adaptor.getName(), message);
        }

        String targetName = path.getRelativePath().getFileNameAsString();
        FTPFile[] listFiles = ftpClient.listDirectories(parent.getAbsolutePath());

        for (FTPFile listFile : listFiles) {
            if (listFile.getName().equals(targetName)) {
                return listFile;
            }
        }
        return null;
    }

    @Override
    public Path readSymbolicLink(Path link) throws XenonException {
        return null;
    }

    @Override
    public void setPosixFilePermissions(Path path, Set<PosixFilePermission> permissions) throws XenonException {
    }

    public void end() {

        LOGGER.debug("end called, closing all file systems");

        //        while (fileSystems.size() > 0) {
        //            Set<String> keys = fileSystems.keySet();
        //            String first = keys.iterator().next();
        //            FileSystem fs = fileSystems.get(first).getImpl();
        //
        //            try {
        //                close(fs);
        //            } catch (XenonException e) {
        //                // ignore for now
        //            }
        //        }

        LOGGER.debug("end OK");
    }
}
