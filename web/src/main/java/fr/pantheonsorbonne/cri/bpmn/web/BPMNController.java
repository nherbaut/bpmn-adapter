package fr.pantheonsorbonne.cri.bpmn.web;

import java.io.File;

import javax.servlet.http.HttpServletResponse;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class BPMNController {

	@GetMapping("/")
	public String greeting(@RequestParam(name = "name", required = false, defaultValue = "World") String name,
			Model model) {
		model.addAttribute("name", name);
		return "index";
	}

	@PostMapping(value = "/bpmn", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	@ResponseBody()
	public FileSystemResource uploadBPMN(@RequestParam("file") MultipartFile file, HttpServletResponse response) {
		BPMNFileService service = new BPMNFileService();
		File res = service.getBPMNFile(file);

		response.setContentType("application/zip");
		response.setHeader("Content-Disposition", "attachment; filename=ms-suite.zip");

		return new FileSystemResource(res);
	}

}
