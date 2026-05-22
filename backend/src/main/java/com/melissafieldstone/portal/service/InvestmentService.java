package com.melissafieldstone.portal.service;

import com.melissafieldstone.portal.dto.AddDocumentRequest;
import com.melissafieldstone.portal.dto.CreateInvestmentRequest;
import com.melissafieldstone.portal.dto.InvestmentResponse;
import com.melissafieldstone.portal.entity.Investment;
import com.melissafieldstone.portal.entity.InvestmentDocument;
import com.melissafieldstone.portal.entity.Investor;
import com.melissafieldstone.portal.repository.InvestmentDocumentRepository;
import com.melissafieldstone.portal.repository.InvestmentRepository;
import com.melissafieldstone.portal.repository.InvestorCredentialsRepository;
import com.melissafieldstone.portal.repository.InvestorRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class InvestmentService {

    private final InvestmentRepository investmentRepo;
    private final InvestmentDocumentRepository documentRepo;
    private final InvestorRepository investorRepo;
    private final InvestorCredentialsRepository credentialsRepo;

    @Value("${app.upload-dir:/uploads}")
    private String uploadDir;

    public List<InvestmentResponse> getAllInvestments() {
        return investmentRepo.findAllByDeletedFalse().stream().map(this::toResponse).toList();
    }

    public InvestmentResponse getInvestmentById(Integer id) {
        Investment investment = investmentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        return toResponse(investment);
    }

    public InvestmentResponse createInvestment(CreateInvestmentRequest request) {
        Investment investment = new Investment();
        investment.setName(request.getName());
        investment.setStartDate(request.getStartDate());
        investment.setAnticipatedEndDate(request.getAnticipatedEndDate());
        investment.setEndDate(request.getEndDate());
        investment.setCurrentUpdate(request.getCurrentUpdate());
        if (request.getProjectProgress() != null) {
            investment.setProjectProgress(request.getProjectProgress());
        }
        if (request.getInvestorIds() != null) {
            List<Investor> investors = investorRepo.findAllById(request.getInvestorIds());
            investment.setInvestors(new ArrayList<>(investors));
        }
        return toResponse(investmentRepo.save(investment));
    }

    public InvestmentResponse updateInvestment(Integer id, CreateInvestmentRequest request) {
        Investment investment = investmentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        if (request.getName() != null) investment.setName(request.getName());
        if (request.getStartDate() != null) investment.setStartDate(request.getStartDate());
        investment.setAnticipatedEndDate(request.getAnticipatedEndDate());
        investment.setEndDate(request.getEndDate());
        investment.setCurrentUpdate(request.getCurrentUpdate());
        if (request.getProjectProgress() != null) {
            investment.setProjectProgress(request.getProjectProgress());
        }
        if (request.getInvestorIds() != null) {
            List<Investor> investors = investorRepo.findAllById(request.getInvestorIds());
            investment.setInvestors(new ArrayList<>(investors));
        }
        return toResponse(investmentRepo.save(investment));
    }

    public void deleteInvestment(Integer id) {
        Investment investment = investmentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        investment.setDeleted(true);
        investmentRepo.save(investment);
    }

    public InvestmentResponse uploadDocument(Integer investmentId, String name, MultipartFile file) {
        Investment investment = investmentRepo.findById(investmentId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            String filename = UUID.randomUUID() + "_" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
            Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            InvestmentDocument doc = new InvestmentDocument();
            doc.setInvestment(investment);
            doc.setName(name);
            doc.setUrl(filename);
            documentRepo.save(doc);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage());
        }
        return toResponse(investmentRepo.findById(investmentId).orElseThrow());
    }

    public InvestmentResponse addDocument(Integer investmentId, AddDocumentRequest request) {
        Investment investment = investmentRepo.findById(investmentId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        InvestmentDocument doc = new InvestmentDocument();
        doc.setInvestment(investment);
        doc.setName(request.getName());
        doc.setUrl(request.getUrl());
        documentRepo.save(doc);
        return toResponse(investmentRepo.findById(investmentId).orElseThrow());
    }

    public InvestmentResponse updateDocument(Integer documentId, String newName) {
        InvestmentDocument doc = documentRepo.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        doc.setName(newName);
        documentRepo.save(doc);
        return toResponse(investmentRepo.findById(doc.getInvestment().getInvestmentId()).orElseThrow());
    }

    public void removeDocument(Integer documentId) {
        documentRepo.deleteById(documentId);
    }

    @Transactional(readOnly = true)
    public void streamDocument(Integer documentId, String username, HttpServletResponse response) throws IOException {
        InvestmentDocument doc = documentRepo.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Integer investorId = credentialsRepo.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN))
                .getInvestor().getInvestorId();

        boolean hasAccess = doc.getInvestment().getInvestors().stream()
                .anyMatch(inv -> inv.getInvestorId().equals(investorId));
        if (!hasAccess) throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        String url = doc.getUrl();
        String filename = url.contains("_") ? url.substring(url.indexOf('_') + 1) : url;
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=\"" + filename + "\"");

        if (!url.startsWith("http")) {
            // Local file
            Path filePath = Paths.get(uploadDir).resolve(url);
            if (!Files.exists(filePath)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            try (InputStream in = Files.newInputStream(filePath)) {
                StreamUtils.copy(in, response.getOutputStream());
            }
        } else {
            // Legacy external URL (Google Drive etc.)
            String pdfUrl = toDriveDownloadUrl(url);
            HttpURLConnection conn = (HttpURLConnection) new URL(pdfUrl).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (InputStream in = conn.getInputStream()) {
                StreamUtils.copy(in, response.getOutputStream());
            } finally {
                conn.disconnect();
            }
        }
    }

    private String toDriveDownloadUrl(String url) {
        Matcher m = Pattern.compile("/d/([a-zA-Z0-9_-]+)").matcher(url);
        if (m.find()) return "https://drive.google.com/uc?export=download&id=" + m.group(1);
        Matcher m2 = Pattern.compile("[?&]id=([a-zA-Z0-9_-]+)").matcher(url);
        if (m2.find()) return "https://drive.google.com/uc?export=download&id=" + m2.group(1);
        return url;
    }

    public List<InvestmentResponse> getInvestmentsForInvestor(String username) {
        Integer investorId = credentialsRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Investor not found"))
                .getInvestor().getInvestorId();
        return investmentRepo.findByInvestors_InvestorIdAndDeletedFalse(investorId)
                .stream().map(this::toResponse).toList();
    }

    public InvestmentResponse toResponse(Investment i) {
        InvestmentResponse r = new InvestmentResponse();
        r.setInvestmentId(i.getInvestmentId());
        r.setName(i.getName());
        r.setStartDate(i.getStartDate());
        r.setAnticipatedEndDate(i.getAnticipatedEndDate());
        r.setEndDate(i.getEndDate());
        r.setCurrentUpdate(i.getCurrentUpdate());
        r.setProjectProgress(i.getProjectProgress());
        r.setInvestors(i.getInvestors().stream().map(inv -> {
            InvestmentResponse.InvestorSummary s = new InvestmentResponse.InvestorSummary();
            s.setInvestorId(inv.getInvestorId());
            s.setFirstName(inv.getFirstName());
            s.setLastName(inv.getLastName());
            s.setEmail(inv.getEmail());
            return s;
        }).toList());
        r.setDocuments(i.getDocuments().stream().map(doc -> {
            InvestmentResponse.DocumentSummary ds = new InvestmentResponse.DocumentSummary();
            ds.setDocumentId(doc.getDocumentId());
            ds.setName(doc.getName());
            ds.setUrl(doc.getUrl());
            return ds;
        }).toList());
        return r;
    }
}
