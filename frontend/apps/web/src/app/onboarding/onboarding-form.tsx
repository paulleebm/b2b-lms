"use client";

import { useState, useTransition } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button, Field, Input, Notice, Panel, PanelBody, PanelFooter, PanelHeader } from "@lms/ui";

import { createTenantSchema, type CreateTenantInput } from "@/lib/tenant-schema";

import { createTenant } from "./actions";

type FieldName = keyof CreateTenantInput;

const FIELDS: { name: FieldName; label: string; placeholder: string; hint: string; autoComplete: string }[] = [
  { name: "name", label: "회사명", placeholder: "에이크미 주식회사", hint: "직원들에게 보이는 이름입니다.", autoComplete: "organization" },
  {
    name: "slug",
    label: "회사 코드",
    placeholder: "acme",
    hint: "영문 소문자, 숫자, 하이픈(-) 3~30자. 나중에 바꿀 수 없습니다.",
    autoComplete: "off",
  },
  {
    name: "domain",
    label: "회사 이메일 도메인",
    placeholder: "acme.com",
    hint: "이 도메인의 이메일을 쓰는 직원이 회사 로그인으로 안내됩니다.",
    autoComplete: "off",
  },
];

/**
 * craft/form-validation: validate on first blur ("onTouched"), re-validate on input while
 * invalid, everything on submit. The same zod schema runs again in the server action;
 * server field errors come back into the same fields and input is never cleared.
 */
export function OnboardingForm() {
  const [isPending, startTransition] = useTransition();
  const [serverError, setServerError] = useState<{ message: string; traceId?: string } | null>(null);

  const form = useForm<CreateTenantInput>({
    resolver: zodResolver(createTenantSchema),
    defaultValues: { name: "", slug: "", domain: "" },
    mode: "onTouched",
  });
  const { errors } = form.formState;

  const onSubmit = form.handleSubmit((values) => {
    setServerError(null);
    startTransition(async () => {
      const result = await createTenant(values);
      if (result.ok) {
        // Full-page navigation: /auth/login is a route handler that redirects to Keycloak.
        window.location.assign(result.redirectTo);
        return;
      }
      for (const [field, message] of Object.entries(result.fieldErrors ?? {})) {
        form.setError(field as FieldName, { type: "server", message });
      }
      setServerError({ message: result.message, traceId: result.traceId });
    });
  });

  return (
    <Panel className="w-full max-w-lg">
      <PanelHeader
        title="회사 등록"
        description="등록한 계정이 이 회사의 첫 관리자가 됩니다. 초대를 기다리는 중이라면 건너뛰어도 됩니다."
      />
      <form onSubmit={onSubmit} noValidate>
        <PanelBody className="grid gap-5">
          {serverError ? (
            <Notice tone="danger" title="회사를 등록하지 못했습니다">
              <p>{serverError.message}</p>
              {serverError.traceId ? (
                <p className="mt-1 text-caption text-muted">
                  문의 코드 <code className="font-mono tabular select-all">{serverError.traceId}</code>
                </p>
              ) : null}
            </Notice>
          ) : null}

          {FIELDS.map((field) => (
            <Field key={field.name} label={field.label} hint={field.hint} error={errors[field.name]?.message}>
              <Input
                id={`tenant-${field.name}`}
                placeholder={field.placeholder}
                autoComplete={field.autoComplete}
                disabled={isPending}
                {...form.register(field.name)}
              />
            </Field>
          ))}
        </PanelBody>
        <PanelFooter>
          <Button type="submit" loading={isPending}>
            {isPending ? "등록 중" : "회사 등록"}
          </Button>
        </PanelFooter>
      </form>
    </Panel>
  );
}
