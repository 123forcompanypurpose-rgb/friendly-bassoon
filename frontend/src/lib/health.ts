export type ApiHealth = {
  status: "UP" | "DOWN";
};

export type HealthResult =
  | { available: true; status: ApiHealth["status"] }
  | { available: false; status: "UNAVAILABLE" };

export async function getApiHealth(): Promise<HealthResult> {
  const apiUrl =
    process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api/v1";

  try {
    const response = await fetch(`${apiUrl}/health`, { cache: "no-store" });
    const health = (await response.json()) as ApiHealth;

    if (!response.ok || (health.status !== "UP" && health.status !== "DOWN")) {
      return { available: false, status: "UNAVAILABLE" };
    }

    return { available: true, status: health.status };
  } catch {
    return { available: false, status: "UNAVAILABLE" };
  }
}
