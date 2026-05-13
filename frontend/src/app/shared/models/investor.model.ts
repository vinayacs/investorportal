export interface Investor {
  investorId: number;
  firstName: string;
  lastName: string;
  email: string;
  emailAddress2: string;
  phone: string;
  mailingAddress: string;
  beneficiaryName: string;
  beneficiaryPhone: string;
  numberOfShares: number;
  investmentSource: string;
  llcName: string;
  majorId: string;
  companyShareHolding: number;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  role: string;
  firstName: string;
  lastName: string;
}

export interface InvestorSummary {
  investorId: number;
  firstName: string;
  lastName: string;
  email: string;
}

export interface InvestmentDocument {
  documentId: number;
  name: string;
  url: string;
}

export interface Investment {
  investmentId: number;
  name: string;
  startDate: string;
  anticipatedEndDate: string | null;
  endDate: string | null;
  currentUpdate: string | null;
  projectProgress: number;
  investors: InvestorSummary[];
  documents: InvestmentDocument[];
}

export interface LoginLog {
  logId: number;
  investorId: number;
  investorName: string;
  loginTimestamp: string;
  ipAddress: string;
  status: string;
  failureReason: string;
  action: string;
}
