package fr.pantheonsorbonne.cri.bpmn.web;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.web.multipart.MultipartFile;

import fr.pantheonsorbonne.cri.BPMNFacade;
import fr.pantheonsorbonne.cri.bpmn.GRPCFacade;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;

public class BPMNFileService {

	public File getBPMNFile(MultipartFile f) {
		try {
			Path userPayloadZipFile = Files.createTempFile("bpmnFileService", ".zip");
			Path userPayloadZipFileUnpackedDir = Files.createTempDirectory("bpmnFileServiceDir");
			Path userResponseUnpackedDir = Files
					.createTempDirectory(userPayloadZipFileUnpackedDir.getFileName().toString());
			Path userResponseZipFile = Files.createTempFile(userPayloadZipFile.getFileName().toString(), ".zip");
			try (FileOutputStream fos = new FileOutputStream(userPayloadZipFile.toFile())) {
				fos.write(f.getBytes());
			}

			ZipFile zipFileUserRequestPayload = new ZipFile(userPayloadZipFile.toFile());
			zipFileUserRequestPayload.extractAll(userPayloadZipFileUnpackedDir.toFile().getPath());
			Optional<Path> bpmn2File = Files.find(userPayloadZipFileUnpackedDir, 99, //
					(p, u) -> p.toString().endsWith(".bpmn2"))//
					.findFirst();

			if (!bpmn2File.isPresent()) {
				throw new RuntimeException("failed to find a bpmn2 in the zip archive");
			}

			BPMNFacade facade = new BPMNFacade(bpmn2File.get().toFile());
			facade.writeOpenAPIArtefacts(userResponseUnpackedDir.toFile());
			Collection<File> openApiSpecs = facade.writeOpenApi(userResponseUnpackedDir.toFile());

			Files.delete(userResponseZipFile);

			for (File openApiSpec : openApiSpecs) {
				GRPCFacade grpcFacade = new GRPCFacade(openApiSpec);
				File grpcArtefactsRoot = grpcFacade.generateGRPC();
				Path destinationFolder = Paths.get(userResponseUnpackedDir.toString(),
						com.google.common.io.Files.getNameWithoutExtension(openApiSpec.toString()));
				if (!destinationFolder.toFile().exists()) {
					if (!destinationFolder.toFile().mkdirs()) {
						throw new RuntimeException("failed to create " + destinationFolder.toString());
					}
				}
				Files.walk(grpcArtefactsRoot.toPath(), 1).filter(p -> !p.equals(grpcArtefactsRoot.toPath()))
						.forEach(p -> safeMove(p, destinationFolder));
			}

			// put openapi files in corresponding folder
			Files.walk(userResponseUnpackedDir, 1)//
					.filter(p -> Files.isRegularFile(p))//
					.forEach(p -> safeMove(p, Paths.get(userResponseUnpackedDir.toString(),
							com.google.common.io.Files.getNameWithoutExtension(p.toString()))));

			List<File> openApiRestServerStubsFolder = Files.walk(userResponseUnpackedDir, 1)
					.filter(p -> Files.isDirectory(p)).map(p -> p.toFile()).collect(Collectors.toList());

			ZipFile zipfileUserResponse = new ZipFile(userResponseZipFile.toFile());
			for (File folder : openApiRestServerStubsFolder) {
				if (!folder.equals(userResponseUnpackedDir.toFile())) {
					zipfileUserResponse.addFolder(folder);
				}
			}

			return userResponseZipFile.toFile();

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void safeMove(Path from, Path to) {
		try {
			if (!to.toFile().exists()) {
				if (!to.toFile().mkdirs())
					throw new IOException("failed to create " + to.toString());
			}
			Files.move(from, Paths.get(to.toString(), from.getFileName().toString()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

}
