package org.baozi;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        long startTime = System.nanoTime();
//        String originalFilePath = "/home/baozi/study/test-files/original-file/drillstand-GAS-1-15 - 单位 - 连续轻烃.xlsx";
        String originalFilePath = "/home/baozi/study/test-files/original-file/20200602_085708_ev_fixed.mp4";
        String tempFilePath = "/home/baozi/study/test-files/temp-file";
        String sliceRecordPath = tempFilePath + "/record.bin";
        String mergeFilePath = "/home/baozi/study/test-files/merge-file";

        File tempFilePathFile = Paths.get(tempFilePath).toFile();
        if (!tempFilePathFile.exists()) {
            tempFilePathFile.mkdirs();
        }

        File mergeFilePathFile = Paths.get(mergeFilePath).toFile();
        if (!mergeFilePathFile.exists()) {
            mergeFilePathFile.mkdirs();
        }

//        splitFile(originalFilePath, tempFilePath, sliceRecordPath);
        mergeFile(sliceRecordPath, mergeFilePath + "/aa1.mp4");
        long endTime = System.nanoTime();
        System.out.println("耗时：" + (endTime - startTime) / 1000000.0 + "ms");
    }

    public record TempFileRecord(
            String filePath,
            long startPos
    ) implements Serializable {
    }

    public static void splitFile(String filePath, String sliceOutputPath, String sliceRecordPath) throws Exception {
        long startPos = 0L;
        int batchSize = 10240, sliceNum = 0, maxFileLenPerSliceFile = 1024 * 1024;
        File readFile = new File(filePath);
        long totalLength = readFile.length();
        LinkedList<TempFileRecord> tempFileRecords = new LinkedList<>();

        long leftFileLen = totalLength;
        while (leftFileLen > 0) {
            String tempFilePath = sliceOutputPath + "/" + sliceNum + ".temp";
            File writeFile = new File(tempFilePath);
            // 分片存文件，返回分片文件大小（字节）
            long sliceFileLength = writeSliceFile(readFile, writeFile, startPos, batchSize,
                    maxFileLenPerSliceFile > leftFileLen ? leftFileLen : maxFileLenPerSliceFile);
            TempFileRecord tempFileRecord = new TempFileRecord(tempFilePath, startPos);
            startPos += sliceFileLength;
            tempFileRecords.add(tempFileRecord);
            sliceNum++;
            leftFileLen = totalLength - startPos;
        }
        // 写入分片文件结构数据
        try (
                FileOutputStream fileOutputStream = new FileOutputStream(sliceRecordPath);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
            objectOutputStream.writeObject(tempFileRecords);
            objectOutputStream.flush();
        }
    }

    private static long writeSliceFile(File readFile, File writeFile, long startPos, int batchSize, long maxFileLenPerSliceFile) throws Exception {
        byte[] bytes = new byte[batchSize];
        long sliceFileLength = 0L;
        try (
                RandomAccessFile read = new RandomAccessFile(readFile, "r");
                RandomAccessFile write = new RandomAccessFile(writeFile, "rw")
        ) {
            read.seek(startPos);
            int batchReadLen;
            long leftDataLen = maxFileLenPerSliceFile;
            int readBatchSize = (int) (batchSize > leftDataLen ? leftDataLen : batchSize);
            while ((batchReadLen = read.read(bytes, 0, readBatchSize)) != -1 && leftDataLen > 0) {
                sliceFileLength += batchReadLen;
                leftDataLen = maxFileLenPerSliceFile - sliceFileLength;
                readBatchSize = (int) (batchSize > leftDataLen ? leftDataLen : batchSize);
                if (batchReadLen < batchSize) {
                    // 最后一次写入
                    write.write(bytes, 0, batchReadLen);
                    break;
                } else {
                    write.write(bytes, 0, batchSize);
                }
            }
        }
        return sliceFileLength;
    }

    public static void mergeFile(String sliceRecordPath, String mergeFilePath) throws Exception {
        List<TempFileRecord> tempFileRecordList;
        try (
                FileInputStream fileInputStream = new FileInputStream(sliceRecordPath);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)
        ) {
            tempFileRecordList = (List<TempFileRecord>) objectInputStream.readObject();
        }

        int batchSize = 102400;
        byte[] bytes = new byte[batchSize];
        File mergeFile = new File(mergeFilePath);
        try (
                RandomAccessFile mergeAccessFile = new RandomAccessFile(mergeFile, "rw")
        ) {

//            tempFileRecordList.parallelStream().forEach(tempFileRecord -> {
//                try (FileInputStream fileInputStream = new FileInputStream(tempFileRecord.filePath());
//                     BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {
//                    int readBytes;
//                    mergeAccessFile.seek(tempFileRecord.startPos());
//                    while ((readBytes = bufferedInputStream.readNBytes(bytes, 0, batchSize)) != 0) {
//                        mergeAccessFile.write(bytes, 0, readBytes);
//                    }
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//            });


//            for (TempFileRecord tempFileRecord : tempFileRecordList) {
//                try (FileInputStream fileInputStream = new FileInputStream(tempFileRecord.filePath());
//                     BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {
//                    int readBytes;
//                    mergeAccessFile.seek(tempFileRecord.startPos());
//                    while ((readBytes = bufferedInputStream.readNBytes(bytes, 0, batchSize)) != 0) {
//                        mergeAccessFile.write(bytes, 0, readBytes);
//                    }
//                }
//            }


//            FileChannel writeChannel = mergeAccessFile.getChannel();
//            for (TempFileRecord tempFileRecord : tempFileRecordList) {
//                String tempFilePathStr = tempFileRecord.filePath();
//                Path tempFilePath = Paths.get(tempFilePathStr);
//                MappedByteBuffer readMappedByteBuffer = null;
//                MappedByteBuffer writeMappedByteBuffer = null;
//                try (
//                        FileChannel readChannel = (FileChannel) Files.newByteChannel(tempFilePath, StandardOpenOption.READ);
//                ) {
//
//                    BasicFileAttributeView fileAttributeView = Files.getFileAttributeView(tempFilePath, BasicFileAttributeView.class);
//                    BasicFileAttributes basicFileAttributes = fileAttributeView.readAttributes();
//                    long readFileByteLen = basicFileAttributes.size();
//                    readMappedByteBuffer = readChannel.map(FileChannel.MapMode.READ_ONLY, 0, readFileByteLen);
//
//
//                    writeMappedByteBuffer = writeChannel.map(FileChannel.MapMode.READ_WRITE, tempFileRecord.startPos(), readFileByteLen);
//
//                    writeMappedByteBuffer.put(readMappedByteBuffer);
//
//                } finally {
//                    if (readMappedByteBuffer != null) {
//                        readMappedByteBuffer.clear();
//                    }
//                    if (writeMappedByteBuffer != null) {
//                        writeMappedByteBuffer.clear();
//                    }
//                }
//            }


//            FileChannel writeChannel = mergeAccessFile.getChannel();
//            for (TempFileRecord tempFileRecord : tempFileRecordList) {
//                String tempFilePathStr = tempFileRecord.filePath();
//                Path tempFilePath = Paths.get(tempFilePathStr);
//                ByteBuffer byteBuffer = null;
//                try (
//                        FileChannel readChannel = (FileChannel) Files.newByteChannel(tempFilePath, StandardOpenOption.READ);
//                ) {
//
//
//                    BasicFileAttributeView fileAttributeView = Files.getFileAttributeView(tempFilePath, BasicFileAttributeView.class);
//                    BasicFileAttributes basicFileAttributes = fileAttributeView.readAttributes();
//                    long readFileByteLen = basicFileAttributes.size();
//                    byteBuffer = ByteBuffer.allocateDirect((int) readFileByteLen);
//
//                    readChannel.read(byteBuffer);
//
//                    byteBuffer.flip();
//
//                    writeChannel.position(tempFileRecord.startPos());
//                    writeChannel.write(byteBuffer);
//
//                }
//            }


//            FileChannel writeChannel = mergeAccessFile.getChannel();
//            for (TempFileRecord tempFileRecord : tempFileRecordList) {
//                String tempFilePathStr = tempFileRecord.filePath();
//                Path tempFilePath = Paths.get(tempFilePathStr);
//                try (
//                        FileChannel readChannel = (FileChannel) Files.newByteChannel(tempFilePath, StandardOpenOption.READ);
//                ) {
//
//
//                    BasicFileAttributeView fileAttributeView = Files.getFileAttributeView(tempFilePath, BasicFileAttributeView.class);
//                    BasicFileAttributes basicFileAttributes = fileAttributeView.readAttributes();
//                    long readFileByteLen = basicFileAttributes.size();
//
//                    writeChannel.position(tempFileRecord.startPos());
//                    readChannel.transferTo(0, readFileByteLen, writeChannel);
//
//                }
//            }
//            writeChannel.close();

            final FileChannel writeChannel = mergeAccessFile.getChannel();
            tempFileRecordList.parallelStream().forEach(tempFileRecord -> {
                String tempFilePathStr = tempFileRecord.filePath();
                Path tempFilePath = Paths.get(tempFilePathStr);
                try (
                        FileChannel readChannel = (FileChannel) Files.newByteChannel(tempFilePath, StandardOpenOption.READ);
                ) {


                    BasicFileAttributeView fileAttributeView = Files.getFileAttributeView(tempFilePath, BasicFileAttributeView.class);
                    BasicFileAttributes basicFileAttributes = fileAttributeView.readAttributes();
                    long readFileByteLen = basicFileAttributes.size();

                    writeChannel.position(tempFileRecord.startPos());
                    readChannel.transferTo(0, readFileByteLen, writeChannel);

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            writeChannel.close();

        }
    }
}
