import { cookies } from "next/headers";
import { ApiError, getServerJson, type UserProfile } from "./client";

export async function getServerSession(): Promise<UserProfile | null> {
  const cookieHeader = (await cookies())
    .getAll()
    .map(({ name, value }) => `${name}=${value}`)
    .join("; ");
  if (!cookieHeader) return null;

  try {
    return await getServerJson<UserProfile>("/me", cookieHeader);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}
