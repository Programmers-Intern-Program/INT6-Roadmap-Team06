import { ScreenShell } from "@/components/screen-shell";
import { screens } from "@/config/routes";

export default function Home() {
  return <ScreenShell screen={screens.dashboard} />;
}
