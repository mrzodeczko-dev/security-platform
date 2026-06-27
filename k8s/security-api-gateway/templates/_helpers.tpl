{{ define "app.name" }}
{{ .Chart.Name | trunc 63 | trimSuffix "-" }}
{{ end }}

{{ define "app.fullname" }}
{{ printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end -}}

{{- define "app.namespace" -}}
{{- .Release.Namespace -}}
{{- end -}}

{{- define "app.labels" -}}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}

{{- define "app.selectorLabels" -}}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
