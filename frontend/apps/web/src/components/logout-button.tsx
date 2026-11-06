import { Button, type ButtonProps } from "@lms/ui";

/** Native form POST to the logout route handler (works without JavaScript). */
export function LogoutButton({ variant = "secondary", ...props }: Omit<ButtonProps, "type">) {
  return (
    <form action="/auth/logout" method="post">
      <Button type="submit" variant={variant} {...props}>
        로그아웃
      </Button>
    </form>
  );
}
