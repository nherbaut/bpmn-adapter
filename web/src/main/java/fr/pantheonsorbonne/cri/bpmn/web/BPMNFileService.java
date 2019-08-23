package fr.pantheonsorbonne.cri.bpmn.web;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.web.multipart.MultipartFile;

import fr.pantheonsorbonne.cri.BPMNFacade;
import net.lingala.zip4j.ZipFile;

public class BPMNFileService {

	public File getBPMNFile(MultipartFile f) {
		try {
			Path tempFile = Files.createTempFile("bpmnFileService", ".zip");
			Path tempDir = Files.createTempDirectory("bpmnFileServiceDir");
			Path outputDir = Files.createTempDirectory(tempDir.getFileName().toString());
			Path tempFileRes = Files.createTempFile(tempFile.getFileName().toString(), ".zip");
			try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
				fos.write(f.getBytes());
			}

			ZipFile zipFile = new ZipFile(tempFile.toFile());
			zipFile.extractAll(tempDir.toFile().getPath());
			Optional<Path> bpmn2File = Files.find(tempDir, 99, //
					(p, u) -> p.toString().endsWith(".bpmn2"))//
					.findFirst();

			if (!bpmn2File.isPresent()) {
				throw new RuntimeException("failed to find a bpmn2 in the zip archive");
			}

			BPMNFacade facade = new BPMNFacade(bpmn2File.get().toFile());
			facade.writeOpenAPIArtefacts(outputDir.toFile());
			facade.writeOpenApi(outputDir.toFile());

			Files.delete(tempFileRes);

			List<File> filesToAdd = Files.walk(outputDir, 1).filter(p -> Files.isRegularFile(p)).map(p -> p.toFile())
					.collect(Collectors.toList());
			List<File> foldersToAdd = Files.walk(outputDir, 1).filter(p -> Files.isDirectory(p)).map(p -> p.toFile())
					.collect(Collectors.toList());

			ZipFile zipfile = new ZipFile(tempFileRes.toFile());
			for (File folder : foldersToAdd) {
				if (!folder.equals(outputDir.toFile())) {
					zipfile.addFolder(folder);
				}
			}
			for (File file : filesToAdd) {
				zipfile.addFile(file);
			}

			return tempFileRes.toFile();

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
