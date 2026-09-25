import { redirect } from 'next/navigation'
import { appRoutes } from '@/shared/config'

export default function HomePage() {
    // Executes instantly on the server
    redirect(appRoutes.authenticatedHome)
}
