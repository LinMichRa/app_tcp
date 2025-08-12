package org.vinni.servidor.gui;

import javax.swing.*;
import org.vinni.Config;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;

public class Servidor extends JFrame {
    private JTextArea logArea;
    private JTextField puertoField, mensajeField;
    private JButton iniciarBtn, subirBtn, descargarBtn, enviarMsgBtn, apagarBtn;
    private ServerSocket serverSocket;
    private Socket clienteSocket;
    private DataInputStream in;
    private DataOutputStream out;

    private String downloadFolder = Config.get("downloadFolderServidor"); // Archivos recibidos
    private String uploadFolder = Config.get("uploadFolderServidor");     // Archivos enviados

    public Servidor() {
        setTitle("Servidor");
        setSize(500, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(null);

        JLabel titulo = new JLabel("SERVIDOR", SwingConstants.CENTER);
        titulo.setFont(new Font("Arial", Font.BOLD, 20));
        titulo.setBounds(0, 0, 500, 40);
        add(titulo);

        JLabel puertoLbl = new JLabel("Puerto:");
        puertoLbl.setBounds(20, 50, 60, 25);
        add(puertoLbl);

        puertoField = new JTextField();
        puertoField.setBounds(80, 50, 100, 25);
        add(puertoField);

        iniciarBtn = new JButton("Iniciar");
        iniciarBtn.setBounds(200, 50, 100, 25);
        iniciarBtn.addActionListener(e -> iniciarServidor());
        add(iniciarBtn);

        apagarBtn = new JButton("Apagar");
        apagarBtn.setBounds(310, 50, 100, 25);
        apagarBtn.addActionListener(e -> apagarServidor());
        apagarBtn.setEnabled(false);
        add(apagarBtn);

        subirBtn = new JButton("Enviar archivo");
        subirBtn.setBounds(20, 90, 150, 25);
        subirBtn.addActionListener(e -> enviarArchivo());
        subirBtn.setEnabled(false);
        add(subirBtn);

        descargarBtn = new JButton("Recibir archivo");
        descargarBtn.setBounds(200, 90, 150, 25);
        descargarBtn.addActionListener(e -> recibirArchivo());
        descargarBtn.setEnabled(false);
        add(descargarBtn);

        mensajeField = new JTextField();
        mensajeField.setBounds(20, 130, 250, 25);
        add(mensajeField);

        enviarMsgBtn = new JButton("Enviar mensaje");
        enviarMsgBtn.setBounds(280, 130, 150, 25);
        enviarMsgBtn.addActionListener(e -> enviarMensaje());
        enviarMsgBtn.setEnabled(false);
        add(enviarMsgBtn);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBounds(20, 170, 450, 270);
        add(scroll);
    }

    private void iniciarServidor() {
        try {
            int puerto = Integer.parseInt(puertoField.getText());
            serverSocket = new ServerSocket(puerto);
            log("Servidor iniciado en puerto " + puerto);
            iniciarBtn.setEnabled(false);
            apagarBtn.setEnabled(true);

            new Thread(() -> {
                try {
                    clienteSocket = serverSocket.accept();
                    log("Cliente conectado");
                    in = new DataInputStream(clienteSocket.getInputStream());
                    out = new DataOutputStream(clienteSocket.getOutputStream());
                    habilitarControles(true);
                    escucharMensajes();
                } catch (IOException e) {
                    log("Error: " + e.getMessage());
                }
            }).start();
        } catch (Exception e) {
            log("Error iniciando servidor: " + e.getMessage());
        }
    }

    private void apagarServidor() {
        try {
            if (clienteSocket != null && !clienteSocket.isClosed()) clienteSocket.close();
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            log("Servidor apagado");
            habilitarControles(false);
            iniciarBtn.setEnabled(true);
            apagarBtn.setEnabled(false);
        } catch (IOException e) {
            log("Error al apagar: " + e.getMessage());
        }
    }

    private void habilitarControles(boolean estado) {
        subirBtn.setEnabled(estado);
        descargarBtn.setEnabled(estado);
        enviarMsgBtn.setEnabled(estado);
    }

    private void escucharMensajes() {
        try {
            while (true) {
                String msg = in.readUTF();
                if (msg.startsWith("FILE:")) {
                    String fileName = msg.substring(5);
                    int length = in.readInt();
                    byte[] data = new byte[length];
                    in.readFully(data);
                    Path dest = Paths.get(downloadFolder, fileName);
                    Files.write(dest, data);
                    log("Archivo recibido: " + fileName);
                } else {
                    log("Cliente: " + msg);
                }
            }
        } catch (IOException e) {
            log("Conexión cerrada");
            habilitarControles(false);
        }
    }

    private void enviarMensaje() {
        try {
            out.writeUTF(mensajeField.getText());
            log("Servidor: " + mensajeField.getText());
            mensajeField.setText("");
        } catch (IOException e) {
            log("Error enviando mensaje: " + e.getMessage());
        }
    }

    private void enviarArchivo() {
        try {
            JFileChooser fc = new JFileChooser(uploadFolder);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                out.writeUTF("FILE:" + file.getName());
                byte[] data = Files.readAllBytes(file.toPath());
                out.writeInt(data.length);
                out.write(data);
                log("Archivo enviado: " + file.getName());
            }
        } catch (IOException e) {
            log("Error enviando archivo: " + e.getMessage());
        }
    }

    private void recibirArchivo() {
        try {
            // Se maneja en escucharMensajes()
        } catch (Exception e) {
            log("Error recibiendo archivo: " + e.getMessage());
        }
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
    }

    public static void main(String[] args) {
        new Servidor().setVisible(true);
    }
}
