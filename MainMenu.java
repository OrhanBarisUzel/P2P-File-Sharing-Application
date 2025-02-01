import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.*;

public class MainMenu extends JFrame implements P2PHandling.FileTransferCallback{
    private JMenuBar menuBar;
    private JTextField sharedFolderField;
    private JTextField destinationFolderField;
    private JTextField excludeFilesField;
    private JList<String> excludeFoldersList;
    private JList<String> excludeFilesList;
    private JCheckBox checkNewFilesBox;
    private DefaultListModel<String> excludeFoldersModel;
    private DefaultListModel<String> excludeFilesModel;
    private JList<String> downloadingList;
    private DefaultListModel<String> downloadingModel;
    private JList<String> foundList;
    private DefaultListModel<String> foundModel;
    private P2PHandling P2PHandling;
    private P2PConnection p2pConnection;
    private Map<String, JProgressBar> downloadProgressBars;
    private Thread fileWatcherThread;
    private boolean flag= false;
    private Set<String> knownPeerFiles= new HashSet<>();
    public MainMenu(){
        setTitle("P2P File Sharing Application");
        setSize(500, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        downloadProgressBars= new HashMap<>();
        P2PHandling= new P2PHandling(6789, this);
        p2pConnection= new P2PConnection(6789);
        MenuBar();
        GUI();

        addWindowListener(new WindowAdapter(){
            @Override
            public void windowClosing(WindowEvent e){
                disconnect();
            }
        });
    }

    private void MenuBar(){
        JMenu FileMenu= new JMenu("Files");
        JMenuItem Connect= new JMenuItem("Connect");
        JMenuItem Disconnect= new JMenuItem("Disconnect");
        JMenuItem Exit= new JMenuItem("Exit");
        
        Connect.addActionListener(e -> connect());
        Disconnect.addActionListener(e -> disconnect());
        Exit.addActionListener(e ->{
            disconnect();
            System.exit(0);
        });
        
        FileMenu.add(Connect);
        FileMenu.add(Disconnect);
        FileMenu.add(Exit);
        	
        JMenu HelpMenu= new JMenu("Help");
        JMenuItem About= new JMenuItem("About");
        About.addActionListener(new java.awt.event.ActionListener() {
        	public void actionPerformed(java.awt.event.ActionEvent evt) {
        		JOptionPane.showMessageDialog(null, "Orhan Barış Uzel 20200702125");
        	}
        });
        HelpMenu.add(About);
        
        menuBar= new JMenuBar();
        menuBar.add(FileMenu);
        menuBar.add(HelpMenu);
        setJMenuBar(menuBar);
    }

    private void GUI(){
        JPanel mainPanel= new JPanel();
        mainPanel.setLayout(null);

        // Shared folder section
        JLabel sharedLabel= new JLabel("Root of the P2P shared folder");
        sharedLabel.setBounds(10, 10, 200, 20);
        mainPanel.add(sharedLabel);

        sharedFolderField= new JTextField();
        sharedFolderField.setBounds(10, 30, 380, 20);
        mainPanel.add(sharedFolderField);

        JButton sharedSetButton= new JButton("Set");
        sharedSetButton.setBounds(400, 30, 60, 20);
        sharedSetButton.addActionListener(e -> selectSharedFolder());
        mainPanel.add(sharedSetButton);

        // Destination folder section
        JLabel destLabel= new JLabel("Destination folder");
        destLabel.setBounds(10, 60, 200, 20);
        mainPanel.add(destLabel);

        destinationFolderField= new JTextField();
        destinationFolderField.setBounds(10, 80, 380, 20);
        mainPanel.add(destinationFolderField);

        JButton destSetButton= new JButton("Set");
        destSetButton.setBounds(400, 80, 60, 20);
        destSetButton.addActionListener(e -> selectDestinationFolder());
        mainPanel.add(destSetButton);

        //Settings section
        JLabel settingsLabel= new JLabel("Settings");
        settingsLabel.setBounds(10, 110, 200, 20);
        mainPanel.add(settingsLabel);

        checkNewFilesBox= new JCheckBox("Check new files only in the root");
        checkNewFilesBox.setBounds(10, 130, 200, 20);
        mainPanel.add(checkNewFilesBox);

        // Exclude folders section
        JLabel excludeFoldersLabel= new JLabel("Exclude files under these folders");
        excludeFoldersLabel.setBounds(10, 160, 200, 20);
        mainPanel.add(excludeFoldersLabel);

        excludeFoldersModel= new DefaultListModel<>();
        excludeFoldersList= new JList<>(excludeFoldersModel);
        JScrollPane excludeFoldersScroll= new JScrollPane(excludeFoldersList);
        excludeFoldersScroll.setBounds(10, 180, 200, 80);
        mainPanel.add(excludeFoldersScroll);

        JButton addFolderButton= new JButton("Add");
        addFolderButton.setBounds(10, 265, 60, 20);
        addFolderButton.addActionListener(e -> addExcludedFolder());
        mainPanel.add(addFolderButton);

        JButton delFolderButton= new JButton("Del");
        delFolderButton.setBounds(80, 265, 60, 20);
        delFolderButton.addActionListener(e ->{
            int selectedIndex= excludeFoldersList.getSelectedIndex();
            if (selectedIndex!= -1){
                excludeFoldersModel.remove(selectedIndex);
            }
        });
        mainPanel.add(delFolderButton);

        //mask section
        JLabel excludeFilesLabel= new JLabel("Exclude files matching these masks");
        excludeFilesLabel.setBounds(220, 160, 250, 20);
        mainPanel.add(excludeFilesLabel);

        excludeFilesModel= new DefaultListModel<>();
        excludeFilesList= new JList<>(excludeFilesModel);
        JScrollPane excludeFilesScroll= new JScrollPane(excludeFilesList);
        excludeFilesScroll.setBounds(220, 180, 200, 80);
        mainPanel.add(excludeFilesScroll);

        excludeFilesField= new JTextField();
        excludeFilesField.setBounds(220, 265, 130, 20);
        mainPanel.add(excludeFilesField);

        JButton addFileButton= new JButton("Add");
        addFileButton.setBounds(360, 265, 60, 20);
        addFileButton.addActionListener(e ->{
            String mask= excludeFilesField.getText().trim();
            if (!mask.isEmpty()){
                excludeFilesModel.addElement(mask);
                excludeFilesField.setText("");
            }
        });
        mainPanel.add(addFileButton);

        JButton delFileButton= new JButton("Del");
        delFileButton.setBounds(420, 265, 60, 20);
        delFileButton.addActionListener(e ->{
            int selectedIndex= excludeFilesList.getSelectedIndex();
            if (selectedIndex != -1){
                excludeFilesModel.remove(selectedIndex);
            }
        });
        mainPanel.add(delFileButton);

        //downloading files section
        JLabel downloadingLabel= new JLabel("Downloading files");
        downloadingLabel.setBounds(10, 295, 200, 20);
        mainPanel.add(downloadingLabel);

        downloadingModel= new DefaultListModel<>();
        downloadingList= new JList<>(downloadingModel);
        JScrollPane downloadingScroll= new JScrollPane(downloadingList);
        downloadingScroll.setBounds(10, 315, 460, 150);
        mainPanel.add(downloadingScroll);

        // Found files section
        JLabel foundLabel= new JLabel("Found files");
        foundLabel.setBounds(10, 475, 200, 20);
        mainPanel.add(foundLabel);

        foundModel= new DefaultListModel<>();
        foundList= new JList<>(foundModel);
        JScrollPane foundScroll= new JScrollPane(foundList);
        foundScroll.setBounds(10, 495, 460, 120);
        mainPanel.add(foundScroll);
        JButton searchButton= new JButton("Search");
        searchButton.setBounds(10, 625, 80, 25);
        searchButton.addActionListener(e -> searchFiles());
        mainPanel.add(searchButton);
        add(mainPanel);
        foundList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount()== 2) { 
                    String selected= foundList.getSelectedValue();
                    if(selected != null) {
                        if(selected.contains("]")) {  //For peer file
                            String peerAddress= selected.substring(selected.indexOf("[") + 1, selected.indexOf("]"));
                            String fileName= selected.substring(selected.indexOf("]") + 2);
                            // Get the peer's port
                            Set<Peer> peers= p2pConnection.getPeers();
                            for (Peer peer : peers) {
                                if (peer.getAddress().equals(peerAddress)) {
                                    // Start download
                                    P2PHandling.downloadFile(fileName, peerAddress, peer.getPort());
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private void selectSharedFolder(){
        JFileChooser chooser= new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if(chooser.showOpenDialog(this)== JFileChooser.APPROVE_OPTION){
            String path= chooser.getSelectedFile().getAbsolutePath();
            sharedFolderField.setText(path);
            P2PHandling.setSharedFolder(path);
        }
    }

    private void selectDestinationFolder(){
        JFileChooser chooser= new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this)== JFileChooser.APPROVE_OPTION){
            String path= chooser.getSelectedFile().getAbsolutePath();
            destinationFolderField.setText(path);
            P2PHandling.setDownloadFolder(path);
        }
    }

    private void addExcludedFolder(){
        JFileChooser chooser= new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this)== JFileChooser.APPROVE_OPTION){
            excludeFoldersModel.addElement(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void connect(){
        if (sharedFolderField.getText().isEmpty() || destinationFolderField.getText().isEmpty()){
            JOptionPane.showMessageDialog(this, 
                "Please set both shared and destination folders first!", 
                "Configuration Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        try{
            P2PHandling.start();
            p2pConnection.start();
            flag= true;
            startFileWatcher();
            startPeerScanner();
            JOptionPane.showMessageDialog(this, "Connected successfully!");
        } catch (Exception e){
            JOptionPane.showMessageDialog(this, 
                "Failed to start: " + e.getMessage(), 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void disconnect(){
        flag= false;
        if (fileWatcherThread != null){
            fileWatcherThread.interrupt();
        }
        P2PHandling.stop();
        p2pConnection.stop();
    }
    private void startPeerScanner(){
        new Thread(() ->{
            while(flag){
                try{
                    updatePeerFiles();
                    Thread.sleep(10000); // Scan peers in seconds
                } catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
    private void updatePeerFiles() {
        if(!flag) return;
        SwingUtilities.invokeLater(() -> {
            Set<String> currentFiles= new HashSet<>();
            Set<Peer> peers= p2pConnection.getPeers();
            //get files from peers
            for (Peer peer: peers) {
                try {
                    Socket socket= new Socket(peer.getAddress(), peer.getPort());
                    DataInputStream dis= new DataInputStream(socket.getInputStream());
                    DataOutputStream dos= new DataOutputStream(socket.getOutputStream());
                    dos.writeUTF("REQUEST_FILE_LIST");
                    
                    int fileCount= dis.readInt();
                    for(int i= 0; i < fileCount; i++) {
                        String fileName= dis.readUTF();
                        dis.readLong();
                        dis.readUTF();    
                        String fileEntry= "[" + peer.getAddress() + "] " + fileName;
                        currentFiles.add(fileEntry);
                        
                        //add if and only if the file is not on the list
                        if (!knownPeerFiles.contains(fileEntry)) {
                            foundModel.addElement(fileEntry);
                            knownPeerFiles.add(fileEntry);
                        }
                    }
                    socket.close();
                    
                } catch(IOException e) {
                    System.err.println("Error getting files from peer " + peer.getAddress() + ": " + e.getMessage());
                }
            }
            
            //remove only disconnected peer files
            for (String knownFile : new HashSet<>(knownPeerFiles)) {
                if (!currentFiles.contains(knownFile)) {
                    knownPeerFiles.remove(knownFile);
                    for (int i= 0; i < foundModel.size(); i++) {
                        if (foundModel.get(i).equals(knownFile)) {
                            foundModel.remove(i);
                            break;
                        }
                    }
                }
            }
        });
    }

    private void startFileWatcher(){
        fileWatcherThread= new Thread(() ->{
            while (flag){
                scanSharedFolder();
                try{
                    Thread.sleep(10000); 
                } catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        fileWatcherThread.start();
    }

    private void scanSharedFolder(){
        if (!flag) return;
        SwingUtilities.invokeLater(() ->{
            foundModel.clear();
            File sharedFolder= new File(sharedFolderField.getText());
            if (sharedFolder.exists() && sharedFolder.isDirectory()){
                for (File file : sharedFolder.listFiles()){
                    if (file.isFile() && !isFileExcluded(file)){
                        foundModel.addElement(file.getName());
                    }
                }
            }
        });
    }

    private boolean isFileExcluded(File file){
        if (checkNewFilesBox.isSelected() && !file.getParentFile().getAbsolutePath()
                .equals(sharedFolderField.getText())){
            return true;
        }

        String fileName= file.getName().toLowerCase();
        for (int i= 0; i < excludeFilesModel.size(); i++){
            String pattern= excludeFilesModel.getElementAt(i).toLowerCase();
            if (fileName.matches(pattern.replace("*", ".*"))){
                return true;
            }
        }

        String filePath= file.getAbsolutePath();
        for (int i= 0; i < excludeFoldersModel.size(); i++){
            if (filePath.startsWith(excludeFoldersModel.getElementAt(i))){
                return true;
            }
        }
        return false;
    }
    private void searchFiles() {
        SwingUtilities.invokeLater(() -> {
            foundModel.clear();
            knownPeerFiles.clear();
            // Add local files
            File sharedFolder= new File(sharedFolderField.getText());
            if(sharedFolder.exists() && sharedFolder.isDirectory()) {
                for(File file : sharedFolder.listFiles()) {
                    if(file.isFile() && !isFileExcluded(file)) {
                        foundModel.addElement(file.getName());
                    }
                }
            }

            //add peer files
            Set<Peer> peers= p2pConnection.getPeers();
            if(peers.isEmpty()) {
                JOptionPane.showMessageDialog(this, 
                    "No peers found on the network", 
                    "Search Results", 
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                for (Peer peer: peers) {
                    try {
                        Socket socket= new Socket(peer.getAddress(), peer.getPort());
                        DataInputStream dis= new DataInputStream(socket.getInputStream());
                        DataOutputStream dos= new DataOutputStream(socket.getOutputStream());

                        dos.writeUTF("REQUEST_FILE_LIST");
                        int fileCount= dis.readInt();   
                        for (int i= 0; i < fileCount; i++) {
                            String fileName= dis.readUTF();
                            dis.readLong();
                            dis.readUTF();
                            String fileEntry= "[" + peer.getAddress() + "] " + fileName;
                            foundModel.addElement(fileEntry);
                            knownPeerFiles.add(fileEntry);
                        }
                        
                        socket.close();
                    } catch (IOException e) {
                        System.err.println("Error getting files from peer " + peer.getAddress() + ": " + e.getMessage());
                    }
                }
            }
        });
    }

    // FileTransferCallback implementation
    @Override
    public void onDownloadProgress(String fileName, int progress){
        SwingUtilities.invokeLater(() ->{
            JProgressBar progressBar= downloadProgressBars.get(fileName);
            if (progressBar== null){
                progressBar= new JProgressBar(0, 100);
                downloadProgressBars.put(fileName, progressBar);
                downloadingModel.addElement(fileName + " - 0%");
            }
            progressBar.setValue(progress);
            int index= downloadingModel.indexOf(fileName + " - "+ (progress-1)+ "%");
            if (index >= 0){
                downloadingModel.setElementAt(fileName + " - " + progress + "%", index);
            }
        });
    }

    @Override
    public void onDownloadComplete(String fileName){
        SwingUtilities.invokeLater(() ->{
            downloadProgressBars.remove(fileName);
            int index= -1;
            for(int i= 0; i < downloadingModel.size(); i++){
                if (downloadingModel.get(i).startsWith(fileName)){
                    index= i;
                    break;
                }
            }
            if(index >= 0){
                downloadingModel.removeElementAt(index);
            }
            JOptionPane.showMessageDialog(this, 
                "Download completed: " + fileName, 
                "Information", 
                JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void onDownloadError(String fileName, String error){
        SwingUtilities.invokeLater(() ->{
            JOptionPane.showMessageDialog(this, 
                "Download failed for " + fileName + ": " + error, 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        });
    }

    public static void main(String[] args){
        SwingUtilities.invokeLater(() ->{
            try{
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e){
                e.printStackTrace();
            }
            new MainMenu().setVisible(true);
        });
    }
}