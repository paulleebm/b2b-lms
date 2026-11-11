export { cn } from "./lib/utils";

export { Button, buttonVariants, type ButtonProps } from "./components/button";
export { Field, Input, Label, Select, Textarea, type FieldProps, type SelectProps } from "./components/field";
export { Panel, PanelHeader, PanelBody, PanelFooter } from "./components/panel";
export { Notice, noticeVariants, type NoticeProps } from "./components/notice";
export { CompletionMark, type CompletionMarkProps } from "./components/completion-mark";
export { BrandMark } from "./components/brand-mark";
export { Badge, badgeVariants, StatusBadge, type BadgeProps, type EnrollmentStatus } from "./components/status-badge";
export { Progress, type ProgressProps } from "./components/progress";
export { StepList, type Step, type StepKind, type StepListProps, type StepState } from "./components/step-list";
export { EmptyState, ErrorState, LoadingRegion, Skeleton } from "./components/states";
export { StatStrip, type Stat } from "./components/stat-strip";
export { DataTable, type Column, type DataTableProps, type SortState } from "./components/data-table";
export type { Department as DepartmentOption, LearnerCourse, LearnerRow, MemberRole, MemberRow, MemberStatus } from "./screens/fixtures";
export { BarChart, type BarDatum } from "./components/bar-chart";
export {
  FileUpload,
  formatBytes,
  type FileUploadProps,
  type UploadItem,
  type UploadState,
} from "./components/file-upload";
export {
  CourseOutline,
  type CourseOutlineProps,
  type LessonStatus,
  type LessonType,
  type MoveDirection,
  type OutlineLesson,
  type OutlineSection,
} from "./components/course-outline";
export {
  AppShell,
  PageHeader,
  SidebarNav,
  type AppShellProps,
  type NavItem,
  type NavSection,
} from "./components/app-shell";

export { AdminDashboard, AdminDashboardScreen, type AdminDashboardProps, type DashboardStats } from "./screens/admin-dashboard";
export { LearnerHome, LearnerHomeScreen, type LearnerHomeProps } from "./screens/learner-home";
export {
  AdminMembers,
  AdminMembersScreen,
  type AdminMembersProps,
  type AdminMembersScreenProps,
  type InviteInput,
} from "./screens/admin-members";
export { StudioCourseEditorScreen, type StudioCourseEditorScreenProps } from "./screens/studio-course-editor";
