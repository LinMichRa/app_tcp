package org.vinni.cliente.gui;

import javax.swing.*;
import org.vinni.Config;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class Cliente extends JFrame {
    private JTextArea chatArea;
    private JTextField mensajeField;
    private JButton conectarBtn, desconectarBtn, enviarBtn, enviarFileBtn, enviarVideoBtn;
    private JList<String> userList;
    private DefaultListModel<String> listModel;
    private Socket tcpSocket;
    private DatagramSocket udpSocket;
    private DataInputStream tcpIn;
    private DataOutputStream tcpOut;
    private String username;
    private String currentReceiver;

    public Cliente() {
        setTitle("Cliente de Chat");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setIconImage(new ImageIcon("client_icon.png").getImage());

        // Panel superior
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JTextField usernameField = new JTextField("Usuario" + (new Random().nextInt(1000)), 10);
        JTextField hostField = new JTextField("localhost", 10);
        JTextField puertoField = new JTextField("12345", 5);
        
        conectarBtn = new JButton("Conectar", new ImageIcon("connect_icon.png"));
        conectarBtn.addActionListener(e -> conectar(
            hostField.getText(), 
            Integer.parseInt(puertoField.getText()),
            usernameField.getText()
        ));
        
        desconectarBtn = new JButton("Desconectar", new ImageIcon("disconnect_icon.png"));
        desconectarBtn.setEnabled(false);
        desconectarBtn.addActionListener(e -> desconectar());
        
        topPanel.add(new JLabel("Usuario:"));
        topPanel.add(usernameField);
        topPanel.add(new JLabel("Host:"));
        topPanel.add(hostField);
        topPanel.add(new JLabel("Puerto:"));
        topPanel.add(puertoField);
        topPanel.add(conectarBtn);
        topPanel.add(desconectarBtn);
        
        // Panel central
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(150);
        
        // Lista de usuarios
        listModel = new DefaultListModel<>();
        userList = new JList<>(listModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                currentReceiver = userList.getSelectedValue();
                chatArea.append("\n--- Chat con " + currentReceiver + " ---\n");
            }
        });
        splitPane.setLeftComponent(new JScrollPane(userList));
        
        // Área de chat
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        splitPane.setRightComponent(new JScrollPane(chatArea));
        
        // Panel inferior
        JPanel bottomPanel = new JPanel(new BorderLayout());
        mensajeField = new JTextField();
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        enviarBtn = new JButton("Enviar", new ImageIcon("send_icon.png"));
        enviarBtn.setEnabled(false);
        enviarBtn.addActionListener(e -> enviarMensaje());
        
        enviarFileBtn = new JButton("Enviar Archivo", new ImageIcon("file_icon.png"));
        enviarFileBtn.setEnabled(false);
        enviarFileBtn.addActionListener(e -> enviarArchivo(false));
        
        enviarVideoBtn = new JButton("Enviar Video", new ImageIcon("video_icon.png"));
        enviarVideoBtn.setEnabled(false);
        enviarVideoBtn.addActionListener(e -> enviarArchivo(true));
        
        buttonPanel.add(enviarFileBtn);
        buttonPanel.add(enviarVideoBtn);
        buttonPanel.add(enviarBtn);
        
        bottomPanel.add(mensajeField, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        
        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void conectar(String host, int puerto, String username) {
        try {
            this.username = username;
            
            // Conexión TCP
            tcpSocket = new Socket(host, puerto);
            tcpIn = new DataInputStream(tcpSocket.getInputStream());
            tcpOut = new DataOutputStream(tcpSocket.getOutputStream());
            tcpOut.writeUTF(username);
            
            // Conexión UDP
            udpSocket = new DatagramSocket();
            tcpOut.writeUTF("UDP_INFO");
            tcpOut.writeInt(udpSocket.getLocalPort());
            
            new Thread(this::recibirMensajes).start();
            log("Conectado al servidor");
            habilitarControles(true);
        } catch (IOException e) {
            log("Error de conexión: " + e.getMessage());
        }
    }

    private void recibirMensajes() {
        try {
            while (true) {
                String tipo = tcpIn.readUTF();
                switch (tipo) {
                    case "MSG":
                        String remitente = tcpIn.readUTF();
                        String mensaje = tcpIn.readUTF();
                        chatArea.append("[" + remitente + "]: " + mensaje + "\n");
                        break;
                    case "FILE":
                        String fileRemitente = tcpIn.readUTF();
                        String fileName = tcpIn.readUTF();
                        long fileSize = tcpIn.readLong();
                        recibirArchivoTCP(fileRemitente, fileName, fileSize);
                        break;
                    case "USERLIST":
                        String usuarios = tcpIn.readUTF();
                        actualizarListaUsuarios(usuarios);
                        break;
                }
            }
        } catch (IOException e) {
            log("Desconectado del servidor");
            habilitarControles(false);
        }
    }

    private void actualizarListaUsuarios(String usuarios) {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            for (String user : usuarios.split(",")) {
                if (!user.equals(username)) {
                    listModel.addElement(user);
                }
            }
        });
    }

    private void enviarMensaje() {
        if (currentReceiver == null || currentReceiver.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Seleccione un destinatario");
            return;
        }
        
        try {
            String mensaje = mensajeField.getText();
            tcpOut.writeUTF("MSG");
            tcpOut.writeUTF(currentReceiver);
            tcpOut.writeUTF(mensaje);
            
            chatArea.append("[Tú a " + currentReceiver + "]: " + mensaje + "\n");
            mensajeField.setText("");
        } catch (IOException e) {
            log("Error enviando mensaje: " + e.getMessage());
        }
    }

    private void enviarArchivo(boolean esVideo) {
        if (currentReceiver == null) {
            JOptionPane.showMessageDialog(this, "Seleccione un destinatario");
            return;
        }
        
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            long fileSize = file.length();
            
            // Archivos pequeños por TCP, videos/archivos grandes por UDP
            if (!esVideo && fileSize < 1024 * 1024 * 10) { // < 10MB
                enviarArchivoTCP(file);
            } else {
                enviarArchivoUDP(file, esVideo);
            }
        }
    }

    private void enviarArchivoTCP(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            tcpOut.writeUTF("FILE");
            tcpOut.writeUTF(currentReceiver);
            tcpOut.writeUTF(file.getName());
            tcpOut.writeLong(file.length());
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                tcpOut.write(buffer, 0, bytesRead);
            }
            log("Archivo enviado por TCP: " + file.getName());
        } catch (IOException e) {
            log("Error enviando archivo: " + e.getMessage());
        }
    }

    private void enviarArchivoUDP(File file, boolean esVideo) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            dos.writeUTF(esVideo ? "VIDEO" : "FILE");
            dos.writeUTF(currentReceiver);
            dos.writeUTF(username);
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());
            
            Files.copy(file.toPath(), dos);
            dos.flush();
            
            byte[] data = baos.toByteArray();
            DatagramPacket packet = new DatagramPacket(
                data, data.length,
                tcpSocket.getInetAddress(), tcpSocket.getPort() + 1
            );
            
            udpSocket.send(packet);
            log("Archivo enviado por UDP: " + file.getName());
        } catch (IOException e) {
            log("Error enviando video: " + e.getMessage());
        }
    }

    private void log(String msg) {
        chatArea.append("[Sistema] " + msg + "\n");
    }

    private void habilitarControles(boolean estado) {
        enviarBtn.setEnabled(estado);
        enviarFileBtn.setEnabled(estado);
        enviarVideoBtn.setEnabled(estado);
        desconectarBtn.setEnabled(estado);
        conectarBtn.setEnabled(!estado);
    }

    private void desconectar() {
        try {
            if (tcpSocket != null) tcpSocket.close();
            if (udpSocket != null) udpSocket.close();
            habilitarControles(false);
            log("Desconectado");
        } catch (IOException e) {
            log("Error desconectando: " + e.getMessage());
        }
    }

    private void recibirArchivoTCP(String remitente, String fileName, long fileSize) {
    try {
        Path destPath = Paths.get(Config.get("downloadFolderCliente"), fileName);
        
        // Manejar nombres duplicados
        int counter = 1;
        String originalName = fileName;
        while (Files.exists(destPath)) {
            int dotIndex = originalName.lastIndexOf('.');
            String name = dotIndex > 0 ? originalName.substring(0, dotIndex) : originalName;
            String ext = dotIndex > 0 ? originalName.substring(dotIndex) : "";
            fileName = name + "(" + counter + ")" + ext;
            destPath = Paths.get(Config.get("downloadFolderCliente"), fileName);
            counter++;
        }

        // Recibir el archivo
        try (FileOutputStream fos = new FileOutputStream(destPath.toFile())) {
            byte[] buffer = new byte[4096];
            long remaining = fileSize;
            while (remaining > 0) {
                int read = tcpIn.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                fos.write(buffer, 0, read);
                remaining -= read;
            }
        }

        chatArea.append("[Archivo de " + remitente + "] " + fileName + " recibido (" + 
                       (fileSize/1024) + " KB)\n");
        
        // Mostrar notificación
        JOptionPane.showMessageDialog(this, 
            "Archivo recibido de " + remitente + ":\n" + fileName,
            "Nuevo archivo",
            JOptionPane.INFORMATION_MESSAGE);
    } catch (IOException e) {
        chatArea.append("[Error] No se pudo recibir archivo de " + remitente + "\n");
    }
}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Cliente().setVisible(true));
    }
}