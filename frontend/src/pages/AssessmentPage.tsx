import { useQuery } from '@tanstack/react-query'
import { useHost } from '@alinha/plugin-sdk'
import { PageHeader } from '@alinha/ui'

export function AssessmentPage() {
  const { api } = useHost()
  const ping = useQuery({
    queryKey: ['assessment', 'ping'],
    queryFn: () => api<{ plugin: string; status: string }>('/assessment/ping'),
  })

  return (
    <>
      <PageHeader title="Avaliação" />
      <p>Backend: {ping.isPending ? 'a verificar…' : ping.isError ? 'indisponível' : ping.data.status}</p>
    </>
  )
}
