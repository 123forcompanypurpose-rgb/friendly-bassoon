import { VerifyEmailStatus } from "@/components/password-recovery-form";

export default async function VerifyEmailPage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  const { token } = await searchParams;
  return <VerifyEmailStatus token={token ?? ""} />;
}
