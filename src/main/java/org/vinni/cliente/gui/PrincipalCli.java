package org.vinni.cliente.gui;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PrincipalCli extends javax.swing.JFrame {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    private String downloadFolder = "client_downloads";
    private String uploadFolder = "client_uploads";
    private long maxFileSize = 500 * 1024 * 1024;

    // Componentes de la GUI
    private javax.swing.JButton bConectar;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextArea mensajesTxt;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextField mensajeTxt;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JButton btEnviar;
    private javax.swing.JTextField ipTxt;
    private javax.swing.JTextField puertoTxt;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JButton bDesconectar;
    private javax.swing.JButton bSubirArchivo;
    private javax.swing.JButton bDescargarArchivo;
    private javax.swing.JButton bSeleccionarUpload;
    private javax.swing.JButton bSeleccionarDownload;

    public PrincipalCli() {
        initComponents();
        crearDirectorios();
    }

    private void initComponents() {
        this.setTitle("Cliente TCP");

        bConectar = new javax.swing.JButton();
        bDesconectar = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        mensajesTxt = new javax.swing.JTextArea();
        mensajeTxt = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        btEnviar = new javax.swing.JButton();
        ipTxt = new javax.swing.JTextField();
        puertoTxt = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        bSubirArchivo = new javax.swing.JButton();
        bDescargarArchivo = new javax.swing.JButton();
        bSeleccionarUpload = new javax.swing.JButton();
        bSeleccionarDownload = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        bConectar.setText("CONECTAR");
        bConectar.addActionListener(e -> conectar());
        getContentPane().add(bConectar);
        bConectar.setBounds(300, 40, 120, 30);

        bDesconectar.setText("DESCONECTAR");
        bDesconectar.setEnabled(false);
        bDesconectar.addActionListener(e -> desconectar());
        getContentPane().add(bDesconectar);
        bDesconectar.setBounds(300, 80, 120, 30);

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 14));
        jLabel1.setText("CLIENTE TCP");
        getContentPane().add(jLabel1);
        jLabel1.setBounds(180, 10, 160, 17);

        mensajesTxt.setColumns(20);
        mensajesTxt.setRows(5);
        jScrollPane1.setViewportView(mensajesTxt);
        getContentPane().add(jScrollPane1);
        jScrollPane1.setBounds(20, 250, 450, 200);

        mensajeTxt.setFont(new java.awt.Font("Verdana", 0, 14));
        getContentPane().add(mensajeTxt);
        mensajeTxt.setBounds(20, 180, 300, 30);

        jLabel2.setText("Mensaje:");
        getContentPane().add(jLabel2);
        jLabel2.setBounds(20, 150, 120, 30);

        btEnviar.setText("Enviar Mensaje");
        btEnviar.addActionListener(e -> enviarMensaje());
        getContentPane().add(btEnviar);
        btEnviar.setBounds(330, 180, 140, 30);

        ipTxt.setText("localhost");
        getContentPane().add(ipTxt);
        ipTxt.setBounds(20, 40, 120, 30);

        puertoTxt.setText("12345");
        getContentPane().add(puertoTxt);
        puertoTxt.setBounds(160, 40, 80, 30);

        jLabel3.setText("Servidor:");
        getContentPane().add(jLabel3);
        jLabel3.setBounds(20, 10, 120, 30);

        bSubirArchivo.setText("Subir Archivo");
        bSubirArchivo.setEnabled(false);
        bSubirArchivo.addActionListener(e -> subirArchivo());
        getContentPane().add(bSubirArchivo);
        bSubirArchivo.setBounds(20, 80, 120, 30);

        bDescargarArchivo.setText("Descargar Archivo");
        bDescargarArchivo.setEnabled(false);
        bDescargarArchivo.addActionListener(e -> descargarArchivo());
        getContentPane().add(bDescargarArchivo);
        bDescargarArchivo.setBounds(160, 80, 150, 30);

        bSeleccionarUpload.setText("Seleccionar Carpeta Upload");
        bSeleccionarUpload.addActionListener(e -> seleccionarCarpetaUpload());
        getContentPane().add(bSeleccionarUpload);
        bSeleccionarUpload.setBounds(20, 120, 200, 30);

        bSeleccionarDownload.setText("Seleccionar Carpeta Download");
        bSeleccionarDownload.addActionListener(e -> seleccionarCarpetaDownload());
        getContentPane().add(bSeleccionarDownload);
        bSeleccionarDownload.setBounds(240, 120, 200, 30);

        setSize(new java.awt.Dimension(500, 500));
        setLocationRelativeTo(null);
    }

    private void crearDirectorios() {
        try {
            Files.createDirectories(Paths.get(downloadFolder));
            Files.createDirectories(Paths.get(uploadFolder));
            mensajesTxt.append("Directorios creados:\n- " + downloadFolder + "\n- " + uploadFolder + "\n");
        } catch (IOException e) {
            mensajesTxt.append("Error creando directorios: " + e.getMessage() + "\n");
        }
    }

    private void seleccionarCarpetaUpload() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            uploadFolder = chooser.getSelectedFile().getAbsolutePath();
            mensajesTxt.append("Carpeta upload seleccionada: " + uploadFolder + "\n");
        }
    }

    private void seleccionarCarpetaDownload() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            downloadFolder = chooser.getSelectedFile().getAbsolutePath();
            mensajesTxt.append("Carpeta download seleccionada: " + downloadFolder + "\n");
        }
    }

    private void conectar() {
        String ip = ipTxt.getText();
        int port;
        
        try {
            port = Integer.parseInt(puertoTxt.getText());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Puerto inválido");
            return;
        }

        try {
            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            dataIn = new DataInputStream(socket.getInputStream());
            dataOut = new DataOutputStream(socket.getOutputStream());
            
            bConectar.setEnabled(false);
            bDesconectar.setEnabled(true);
            bSubirArchivo.setEnabled(true);
            bDescargarArchivo.setEnabled(true);
            ipTxt.setEnabled(false);
            puertoTxt.setEnabled(false);
            
            mensajesTxt.append("Conectado al servidor " + ip + ":" + port + "\n");
            
            // Hilo para recibir mensajes del servidor
            new Thread(() -> {
                try {
                    String fromServer;
                    while ((fromServer = in.readLine()) != null) {
                        String finalFromServer = fromServer;
                        SwingUtilities.invokeLater(() -> mensajesTxt.append("Servidor: " + finalFromServer + "\n"));
                    }
                } catch (IOException e) {
                    if (!e.getMessage().equals("Socket closed")) {
                        SwingUtilities.invokeLater(() -> mensajesTxt.append("Error en la conexión: " + e.getMessage() + "\n"));
                    }
                } finally {
                    SwingUtilities.invokeLater(this::desconectar);
                }
            }).start();
            
        } catch (IOException e) {
            mensajesTxt.append("Error conectando al servidor: " + e.getMessage() + "\n");
        }
    }

    private void desconectar() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                mensajesTxt.append("Desconectado del servidor\n");
            }
        } catch (IOException e) {
            mensajesTxt.append("Error desconectando: " + e.getMessage() + "\n");
        }
        
        bConectar.setEnabled(true);
        bDesconectar.setEnabled(false);
        bSubirArchivo.setEnabled(false);
        bDescargarArchivo.setEnabled(false);
        ipTxt.setEnabled(true);
        puertoTxt.setEnabled(true);
    }

    private void enviarMensaje() {
        if (out == null) {
            JOptionPane.showMessageDialog(this, "No conectado a ningún servidor");
            return;
        }
        
        String mensaje = mensajeTxt.getText();
        if (!mensaje.isEmpty()) {
            out.println("MENSAJE");
            out.println(mensaje);
            mensajeTxt.setText("");
        }
    }

    private void subirArchivo() {
        JFileChooser fileChooser = new JFileChooser(uploadFolder);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File archivo = fileChooser.getSelectedFile();
            
            if (archivo.length() > maxFileSize) {
                mensajesTxt.append("Error: El archivo excede el tamaño máximo permitido (" + 
                                  maxFileSize / (1024 * 1024) + "MB)\n");
                return;
            }
            
            new Thread(() -> {
                try {
                    out.println("SUBIR_ARCHIVO");
                    out.println(archivo.getName());
                    out.println(archivo.length());
                    
                    // Esperar confirmación del servidor
                    String respuesta = in.readLine();
                    if (!"OK".equals(respuesta)) {
                        SwingUtilities.invokeLater(() -> 
                            mensajesTxt.append("Servidor rechazó el archivo: " + respuesta + "\n"));
                        return;
                    }
                    
                    // Enviar el archivo
                    try (FileInputStream fis = new FileInputStream(archivo)) {
                        byte[] buffer = new byte[4096];
                        int leidos;
                        
                        while ((leidos = fis.read(buffer)) > 0) {
                            dataOut.write(buffer, 0, leidos);
                        }
                        
                        SwingUtilities.invokeLater(() -> 
                            mensajesTxt.append("Archivo enviado: " + archivo.getName() + "\n"));
                        
                        // Recibir confirmación final
                        String confirmacion = in.readLine();
                        SwingUtilities.invokeLater(() -> 
                            mensajesTxt.append("Servidor: " + confirmacion + "\n"));
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> 
                        mensajesTxt.append("Error enviando archivo: " + e.getMessage() + "\n"));
                }
            }).start();
        }
    }

    private void descargarArchivo() {
        out.println("DESCARGAR_ARCHIVO");
        
        new Thread(() -> {
            try {
                // Recibir información del archivo
                String nombreArchivo = in.readLine();
                if ("CANCELADO".equals(nombreArchivo)) {
                    SwingUtilities.invokeLater(() -> 
                        mensajesTxt.append("Servidor canceló la transferencia\n"));
                    return;
                }
                
                long tamanoArchivo = Long.parseLong(in.readLine());
                
                if (tamanoArchivo > maxFileSize) {
                    out.println("ERROR: El archivo excede el tamaño máximo permitido (" + 
                              maxFileSize / (1024 * 1024) + "MB)");
                    SwingUtilities.invokeLater(() -> 
                        mensajesTxt.append("Error: Archivo demasiado grande para descargar\n"));
                    return;
                }
                
                out.println("OK"); // Confirmar que se puede recibir el archivo
                
                // Crear el archivo local
                Path path = Paths.get(downloadFolder, nombreArchivo);
                try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                    byte[] buffer = new byte[4096];
                    int leidos;
                    long totalLeido = 0;
                    
                    while (totalLeido < tamanoArchivo && 
                          (leidos = dataIn.read(buffer, 0, (int) Math.min(buffer.length, tamanoArchivo - totalLeido))) != -1) {
                        fos.write(buffer, 0, leidos);
                        totalLeido += leidos;
                    }
                    
                    SwingUtilities.invokeLater(() -> {
                        mensajesTxt.append("Archivo recibido: " + nombreArchivo + " (" + 
                                         tamanoArchivo + " bytes)\n");
                        mensajesTxt.append("Guardado en: " + path.toString() + "\n");
                    });
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> 
                    mensajesTxt.append("Error recibiendo archivo: " + e.getMessage() + "\n"));
            }
        }).start();
    }

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new PrincipalCli().setVisible(true));
    }
}