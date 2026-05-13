import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PdfViewerModule } from 'ng2-pdf-viewer';
import { InvestorService } from '../../shared/services/investor.service';
import { AuthService } from '../../shared/services/auth.service';
import { Investment } from '../../shared/models/investor.model';

@Component({
  selector: 'app-investments',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, PdfViewerModule],
  templateUrl: './investments.html',
  styleUrl: './investments.scss'
})
export class InvestmentsComponent implements OnInit {
  investments: Investment[] = [];
  loading = true;
  expandedId: number | null = null;
  pdfBuffers: Record<number, Uint8Array> = {};
  pdfLoading: Record<number, boolean> = {};

  constructor(
    private investorService: InvestorService,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.investorService.getMyInvestments().subscribe({
      next: data => {
        this.investments = data;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  toggle(id: number): void {
    this.expandedId = this.expandedId === id ? null : id;
    if (this.expandedId !== null) {
      const inv = this.investments.find(i => i.investmentId === id);
      inv?.documents.forEach(doc => {
        if (!this.pdfBuffers[doc.documentId] && !this.pdfLoading[doc.documentId]) {
          this.pdfLoading[doc.documentId] = true;
          this.investorService.getDocumentPdf(doc.documentId).subscribe({
            next: data => {
              this.pdfBuffers[doc.documentId] = new Uint8Array(data);
              this.pdfLoading[doc.documentId] = false;
              this.cdr.detectChanges();
            },
            error: () => {
              this.pdfLoading[doc.documentId] = false;
              this.cdr.detectChanges();
            }
          });
        }
      });
    }
    this.cdr.detectChanges();
  }

  logout(): void { this.authService.logout(); }
}
