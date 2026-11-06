import { z } from "zod";

// Shared by the client form (UX) and the server action (the real check). docs/02 C5.

export const SLUG_RE = /^[a-z0-9-]{3,30}$/;

// Hostname with at least one dot and an alphabetic TLD, e.g. acme.com, mail.acme.co.kr.
const DOMAIN_RE = /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/;

export const createTenantSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "회사명을 입력해 주세요.")
    .max(100, "회사명은 100자 이하로 입력해 주세요."),
  slug: z
    .string()
    .trim()
    .regex(SLUG_RE, "영문 소문자, 숫자, 하이픈(-)만 사용해 3~30자로 입력해 주세요."),
  domain: z
    .string()
    .trim()
    .toLowerCase()
    .regex(DOMAIN_RE, "올바른 이메일 도메인을 입력해 주세요. 예: acme.com"),
});

export type CreateTenantInput = z.infer<typeof createTenantSchema>;
