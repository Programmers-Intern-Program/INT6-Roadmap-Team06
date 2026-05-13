export type PortfolioDraftVariantKey = "DONE" | "DONE_IN_PROGRESS" | "ALL";

export type PortfolioDraftVariant = {
  key: PortfolioDraftVariantKey | string;
  label: string;
  content: string;
};

export type PortfolioDraftPayload = {
  format: "PROJECT_WRITEUP" | string;
  variants: PortfolioDraftVariant[];
};

export type PortfolioDraftSummary = {
  draftId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
};

export type PortfolioDraftDetail = PortfolioDraftSummary & {
  draftPayload: PortfolioDraftPayload;
  sourceRefs: unknown;
};

export type PortfolioDraftUpdateRequest = {
  title: string;
  draftPayload: PortfolioDraftPayload;
};
