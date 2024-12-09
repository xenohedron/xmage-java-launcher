package com.xmage.launcher;

import com.xmage.launcher.DownloadTask.Progress;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

/**
 * @author BetaSteward
 */
public abstract class DownloadTask extends SwingWorker<Void, Progress> {

    public static class Progress {
        String text;
        Integer perc;

        public Progress(String text) {
            this.text = text;
            perc = -1;
        }

        public Progress(Integer perc) {
            this.perc = perc;
            text = null;
        }

    }

    private static final int BUFFER_SIZE = 4096;
    private static final Logger logger = LoggerFactory.getLogger(DownloadTask.class);

    private final JProgressBar progressBar;
    private final JTextArea textArea;

    protected DownloadTask(JProgressBar progressBar, JTextArea textArea) {
        this.progressBar = progressBar;
        this.textArea = textArea;
    }

    protected boolean download(URL downloadURL, String saveDirectory) throws IOException {
        try {
            Downloader dl = new Downloader();
            dl.connect(downloadURL, "");

            BufferedInputStream in = dl.getInputStream();

            File temp = new File(saveDirectory + File.separator + "xmage.dl");
            try (FileOutputStream fout = new FileOutputStream(temp)) {
                final byte[] data = new byte[BUFFER_SIZE];
                int count;
                long total = 0;
                long size = dl.getSize();
                publish(0);
                while ((count = in.read(data, 0, BUFFER_SIZE)) != -1) {
                    fout.write(data, 0, count);
                    total += count;
                    publish((int) (total * 100 / size));
                }
            }
            dl.disconnect();
            return true;
        } catch (IOException ex) {
            publish(0);
            cancel(true);
            logger.error("Error: ", ex);
            return false;
        }
    }

    protected void publish(int perc) {
        publish(new Progress(perc));
    }

    protected void publish(String text) {
        publish(new Progress(text));
    }

    @Override
    protected void process(List<Progress> chunks) {
        for (Progress chunk : chunks) {
            if (chunk.perc >= 0) {
                progressBar.setValue(chunk.perc);
            }
            if (chunk.text != null) {
                textArea.append(chunk.text);
            }
        }
    }

    protected void unzip(File from, File to) throws IOException {
        ZipArchiveEntry zipEntry;
        long size = 0;
        long total = 0;

        try (ZipArchiveInputStream zipIn1 = new ZipArchiveInputStream(Files.newInputStream(from.toPath()))) {
            // first calculate the aggregate size for displaying progress
            publish(0);
            while ((zipEntry = zipIn1.getNextEntry()) != null) {
                size += zipEntry.getSize();
            }
        }

        // now write out the files
        try (ZipArchiveInputStream zipIn = new ZipArchiveInputStream(Files.newInputStream(from.toPath()))) {
            while ((zipEntry = zipIn.getNextEntry()) != null) {
                File destPath = new File(to, zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    destPath.mkdirs();
                } else {
                    File pathFile = new File(destPath.getAbsolutePath().substring(0, destPath.getAbsolutePath().lastIndexOf(File.separator)));
                    pathFile.mkdirs();
                    destPath.createNewFile();
                    byte[] data = new byte[BUFFER_SIZE];
                    try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(destPath.toPath()), BUFFER_SIZE)) {
                        int count;
                        while ((count = zipIn.read(data, 0, BUFFER_SIZE)) != -1) {
                            out.write(data, 0, count);
                        }
                    }
                    total += zipEntry.getSize();
                    if (size != 0) {
                        publish((int) (total * 100 / size));
                    }
                }
            }
        }
    }

}
