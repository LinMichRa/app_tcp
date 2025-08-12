package org.vinni.cliente.gui;

import javax.swing.*;
import org.vinni.Config;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;

public class Cliente extends JFrame {
    private JTextArea logArea;
    private JTextField hostField, puertoField, mensajeField;
    private JButton conectarBtn, desconectarBtn, subirBtn, descargarBtn, enviarMsgBtn;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private String downloadFolder = Config.get("downloadFolderCliente");
    private String uploadFolder = Config.get("uploadFolderCliente");

    public Cliente() {
        setTitle("Cliente");
        setSize(500, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(null);

        JLabel titulo = new JLabel("CLIENTE", SwingConstants.CENTER);
        titulo.setFont(new Font("Arial", Font.BOLD, 22));
        titulo.setBounds(0, 0, 500, 40);
        add(titulo);

        JLabel hostLbl = new JLabel("Host:");
        hostLbl.setBounds(20, 50, 50, 25);
        add(hostLbl);

        hostField = new JTextField("localhost");
        hostField.setBounds(70, 50, 120, 25);
        add(hostField);

        JLabel puertoLbl = new JLabel("Puerto:");
        puertoLbl.setBounds(200, 50, 60, 25);
        add(puertoLbl);

        puertoField = new JTextField();
        puertoField.setBounds(260, 50, 60, 25);
        add(puertoField);

        conectarBtn = new JButton("Conectar");
        conectarBtn.setBounds(330, 50, 100, 25);
        conectarBtn.addActionListener(e -> conectar());
        add(conectarBtn);

        desconectarBtn = new JButton("Desconectar");
        desconectarBtn.setBounds(330, 80, 100, 25);
        desconectarBtn.addActionListener(e -> desconectar());
        desconectarBtn.setEnabled(false);
        add(desconectarBtn);

        subirBtn = new JButton("Subir archivo");
        subirBtn.setBounds(20, 90, 150, 25);
        subirBtn.addActionListener(e -> enviarArchivo());
        subirBtn.setEnabled(false);
        add(subirBtn);

        descargarBtn = new JButton("Descargar archivo");
        descargarBtn.setBounds(180, 90, 150, 25);
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

    private void conectar() {
        try {
            String host = hostField.getText();
            int puerto = Integer.parseInt(puertoField.getText());
            socket = new Socket(host, puerto);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            log("Conectado a servidor");
            habilitarControles(true);
            new Thread(this::escucharMensajes).start();
        } catch (IOException e) {
            log("No se pudo conectar: " + e.getMessage());
        }
    }

    private void desconectar() {
        try {
            if (socket != null) socket.close();
            habilitarControles(false);
            log("Desconectado");
        } catch (IOException e) {
            log("Error al desconectar: " + e.getMessage());
        }
    }

    private void habilitarControles(boolean estado) {
        subirBtn.setEnabled(estado);
        descargarBtn.setEnabled(estado);
        enviarMsgBtn.setEnabled(estado);
        desconectarBtn.setEnabled(estado);
        conectarBtn.setEnabled(!estado);
    }

    private void escucharMensajes() {
        try {
            while (true) {
                String msg = in.readUTF();
                if (msg.startsWith("FILE:")) {
                    recibirArchivo(msg);
                } else {
                    log("Servidor: " + msg);
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
            log("Cliente: " + mensajeField.getText());
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

    private void recibirArchivo(String header) {
        try {
            String fileName = header.substring(5);
            int length = in.readInt();
            byte[] data = new byte[length];
            in.readFully(data);
            Path dest = Paths.get(uploadFolder, fileName); // Guardar en carpeta de subidas del cliente
            Files.write(dest, data);
            log("Archivo recibido: " + fileName);
        } catch (IOException e) {
            log("Error recibiendo archivo: " + e.getMessage());
        }
    }

    private void recibirArchivo() {
        log("Esperando archivo del servidor...");
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
    }

    public static void main(String[] args) {
        new Cliente().setVisible(true);
    }
}
