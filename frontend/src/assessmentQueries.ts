import { useQueryClient } from '@tanstack/react-query'
import { useApiMutation, useApiQuery } from '@alinha/plugin-sdk'
import {
  answerPath,
  assessmentPath,
  ASSESSMENTS_PATH,
  KEYS,
  submitPath,
  type AnswerValue,
  type AssessmentDetail,
  type AssessmentPage,
  type AssessmentSummary,
  type SavedAnswer,
  type StartAssessmentRequest,
} from './api'

export function useAssessmentList() {
  return useApiQuery<AssessmentPage>(KEYS.list, ASSESSMENTS_PATH)
}

export function useAssessmentDetail(id: string | undefined) {
  return useApiQuery<AssessmentDetail>(KEYS.detail(id ?? ''), assessmentPath(id ?? ''), { enabled: !!id })
}

/** useApiQuery adds the path to the key: this is the full key of one assessment's detail. */
const detailKey = (id: string) => [...KEYS.detail(id), assessmentPath(id)]

export function useStartAssessment() {
  const queryClient = useQueryClient()
  return useApiMutation<StartAssessmentRequest, AssessmentDetail>({
    path: ASSESSMENTS_PATH,
    invalidates: [KEYS.list],
    onSuccess: (detail) => { queryClient.setQueryData(detailKey(detail.id), detail) },
  })
}

/**
 * Saves one answer. On success the cached assessment gets the answer and the new progress in place, so the page
 * does not reload the whole questionnaire after every click.
 */
export function useSaveAnswer(assessmentId: string) {
  const queryClient = useQueryClient()
  return useApiMutation<{ questionId: string; answer: AnswerValue }, SavedAnswer>({
    method: 'PUT',
    path: ({ questionId }) => answerPath(assessmentId, questionId),
    invalidates: [KEYS.list],
    onSuccess: (saved) => {
      queryClient.setQueryData<AssessmentDetail>(detailKey(assessmentId), (old) => old && applySavedAnswer(old, saved))
    },
  })
}

/**
 * Submits the assessment (assessment#5). The response is the summary, now `evaluated`: the cached detail takes
 * its status, so the page shows the answers as frozen at once. On an error (already submitted elsewhere, an
 * answer missing) the detail is reloaded, so the page does not keep offering a submit that cannot work.
 */
export function useSubmitAssessment(assessmentId: string) {
  const queryClient = useQueryClient()
  return useApiMutation<void, AssessmentSummary>({
    path: submitPath(assessmentId),
    invalidates: [KEYS.list],
    onSuccess: (summary) => {
      queryClient.setQueryData<AssessmentDetail>(detailKey(assessmentId), (old) =>
        old && { ...old, status: summary.status, updatedAt: summary.updatedAt })
    },
    onError: () => queryClient.invalidateQueries({ queryKey: KEYS.detail(assessmentId) }),
  })
}

/** The assessment with one saved answer applied (immutably). */
export function applySavedAnswer(assessment: AssessmentDetail, saved: SavedAnswer): AssessmentDetail {
  return {
    ...assessment,
    progress: saved.progress,
    updatedAt: saved.answeredAt > assessment.updatedAt ? saved.answeredAt : assessment.updatedAt,
    controls: assessment.controls.map((control) => ({
      ...control,
      questions: control.questions.map((q) =>
        q.id === saved.questionId ? { ...q, answer: saved.answer, answeredAt: saved.answeredAt } : q),
    })),
  }
}
