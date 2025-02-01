import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;
import java.nio.file.*;

public class P2PHandling{
    private static final int CHUNK_SIZE= 256000; // 256KB
    private final ExecutorService threadPool;
    private final Map<String, fileInformation> sharedFiles;
    private ServerSocket serverSocket;
    private boolean isRunning;
    private String sharedFolderPath;
    private String downloadFolderPath;
    private int port;
    private FileTransferCallback callback;
    
    public interface FileTransferCallback{
        void onDownloadProgress(String fileName, int progress);
        void onDownloadComplete(String fileName);
        void onDownloadError(String fileName, String error);
    }

    private static class fileInformation{
        String path;
        long size;
        String checksum;
        Map<Integer, byte[]> chunks;
        
        fileInformation(String path) throws IOException{
            this.path= path;
            Path filePath= Paths.get(path);
            
            //verify that the file exists and it is readable
            if (!Files.exists(filePath)){
                throw new FileNotFoundException("File does not exist: " + path);
            }
            if (!Files.isReadable(filePath)){
                throw new IOException("File is not readable: " + path);
            }
            
            this.size= Files.size(filePath);
            this.chunks= new ConcurrentHashMap<>();
            calculateChecksum(filePath);
        }

        private void calculateChecksum(Path filePath) throws IOException{
            try{
                MessageDigest digest= MessageDigest.getInstance("SHA-256");
                // Use NIO for better file handling
                byte[] buffer= new byte[8192];
                try (InputStream in= Files.newInputStream(filePath, StandardOpenOption.READ)){
                    int read;
                    while ((read= in.read(buffer)) != -1){
                        digest.update(buffer, 0, read);
                    }
                }
                checksum= bytesToHex(digest.digest());
            } catch (Exception e){
                throw new IOException("Failed to calculate checksum for " + filePath, e);
            }
        }
    }

    public P2PHandling(int port, FileTransferCallback callback){
        this.port= port;
        this.callback= callback;
        this.threadPool= Executors.newCachedThreadPool();
        this.sharedFiles= new ConcurrentHashMap<>();
    }
    
    public void setSharedFolder(String path){
        this.sharedFolderPath= path;
        // Create directory if it doesn't exist
        try{
            Files.createDirectories(Paths.get(path));
            scanSharedFolder();
        } catch (IOException e){
            System.err.println("Error creating shared folder: " + e.getMessage());
        }
    }

    public void setDownloadFolder(String path){
        this.downloadFolderPath= path;
        // Create directory if it doesn't exist
        try{
            Files.createDirectories(Paths.get(path));
        } catch (IOException e){
            System.err.println("Error creating download folder: " + e.getMessage());
        }
    }

    public void start() throws IOException{
        if (isRunning){
        	return;
        }
        serverSocket= new ServerSocket(port);
        isRunning= true;
        threadPool.execute(() ->{
            while (isRunning){
                try{
                    Socket client= serverSocket.accept();
                    handleClient(client);
                } catch (IOException e){
                    if (isRunning) e.printStackTrace();
                }
            }
        });
    }

    public void stop(){
        isRunning= false;
        try{
            if (serverSocket != null) serverSocket.close();
            threadPool.shutdownNow();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    private void handleClient(Socket client){
        threadPool.execute(() ->{
            try (DataInputStream dis= new DataInputStream(client.getInputStream());
                 DataOutputStream dos= new DataOutputStream(client.getOutputStream())){
                
                String requestType= dis.readUTF();
                if ("REQUEST_FILE_LIST".equals(requestType)){
                    sendFileList(dos);
                } else if ("REQUEST_FILE".equals(requestType)){
                    handleFileRequest(dis, dos);
                }
            } catch (IOException e){
                e.printStackTrace();
            }
        });
    }

    private void sendFileList(DataOutputStream out) throws IOException{
        out.writeInt(sharedFiles.size());
        for (Map.Entry<String, fileInformation> entry: sharedFiles.entrySet()){
            out.writeUTF(entry.getKey());
            out.writeLong(entry.getValue().size);
            out.writeUTF(entry.getValue().checksum);
        }
    }
    private void handleFileRequest(DataInputStream dis, DataOutputStream dos) throws IOException{
        String fileName= dis.readUTF();
        int chunkIndex= dis.readInt();
        fileInformation fileInformation= sharedFiles.get(fileName);
        
        if (fileInformation== null){
            dos.writeInt(-1);
            return;
        }
        try (RandomAccessFile file= new RandomAccessFile(fileInformation.path, "r")){
            file.seek(chunkIndex* CHUNK_SIZE);
            byte[] buffer= new byte[CHUNK_SIZE];
            int read= file.read(buffer);
            
            dos.writeInt(read);
            if (read > 0){
                dos.write(buffer, 0, read);
            }
        }
    }
    public void downloadFile(String fileName, String peerAddress, int peerPort){
        threadPool.execute(() ->{
            try{
                Socket socket= new Socket(peerAddress, peerPort);
                DataInputStream dis= new DataInputStream(socket.getInputStream());
                DataOutputStream dos= new DataOutputStream(socket.getOutputStream());

                //request file information
                dos.writeUTF("REQUEST_FILE_LIST");
                int fileCount= dis.readInt();
                String checksum= null;
                long size= 0;
                for (int i= 0; i < fileCount; i++){
                    String currentFile= dis.readUTF();
                    if (currentFile.equals(fileName)){
                        size= dis.readLong();
                        checksum= dis.readUTF();
                        break;
                    }
                }
                if (checksum== null){
                    callback.onDownloadError(fileName, "File not found");
                    return;
                }
                //start download
                int totalChunks= (int) Math.ceil(size / (double) CHUNK_SIZE);
                File outputFile= new File(downloadFolderPath, fileName);
                RandomAccessFile output= new RandomAccessFile(outputFile, "rw");
                output.setLength(size);

                for (int i= 0; i < totalChunks; i++){
                    dos.writeUTF("REQUEST_FILE");
                    dos.writeUTF(fileName);
                    dos.writeInt(i);

                    int chunkSize= dis.readInt();
                    if (chunkSize > 0){
                        byte[] chunk= new byte[chunkSize];
                        dis.readFully(chunk);
                        output.seek(i* CHUNK_SIZE);
                        output.write(chunk);
                        
                        int progress= (int)(((i + 1.0)/ totalChunks) * 100);
                        callback.onDownloadProgress(fileName, progress);
                    }
                }
                
                output.close();
                socket.close();
                
                //verify download
                fileInformation downloadedFile= new fileInformation(outputFile.getPath());
                if (!downloadedFile.checksum.equals(checksum)){
                    outputFile.delete();
                    callback.onDownloadError(fileName, "Checksum verification failed");
                    return;
                }
                callback.onDownloadComplete(fileName);
            } catch (IOException e){
                callback.onDownloadError(fileName, e.getMessage());
            }
        });
    }
    private void scanSharedFolder(){
        try{
            Path folder= Paths.get(sharedFolderPath);
            if (!Files.exists(folder)){
                //System.err.println("Shared folder doesn't exist: "+ sharedFolderPath);
                return;
            }
            if (!Files.isDirectory(folder)){
                //System.err.println("Specified path isn't a directory: "+ sharedFolderPath);
                return;
            }
            if (!Files.isReadable(folder)){
                //System.err.println("Can't read from shared folder: "+ sharedFolderPath);
                return;
            }

            //clear existing shared files
            sharedFiles.clear();

            // scanning for new files
            try (DirectoryStream<Path> stream= Files.newDirectoryStream(folder)){
                for (Path file : stream){
                    if (Files.isRegularFile(file) && Files.isReadable(file)){
                        try{
                            sharedFiles.put(file.getFileName().toString(), new fileInformation(file.toString()));
                        } catch (IOException e){
                            //System.err.println("processing file error" + file + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e){
            //System.err.println("scanning shared folder error: " + e.getMessage());
        }
    }

    private static String bytesToHex(byte[] hash){
        StringBuilder hexString= new StringBuilder();
        for (byte b: hash){
            String hex= Integer.toHexString(0xff & b);
            if (hex.length()== 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}