require 'yaml'
path = File.expand_path('../../../.github/workflows/aws-stage-ui.yml', __dir__)
w = YAML.load_file(path)
on = w['on'] || w[true]
raise 'unsafe workflow trigger' if on.key?('pull_request_target') || on.key?('workflow_run')
raise 'read-only GitHub token required' unless w['permissions'] == {'contents' => 'read'}
build, deploy = w['jobs'].values_at('build', 'deploy')
raise 'build must have no cloud/private-repo secrets' if build.to_s.include?('secrets.')
raise 'only artifact directory may be uploaded' unless build['steps'].find { |s| s['id'] == 'artifact' }['with']['path'] == '.build/release/'
raise 'deployment requires successful build' unless deploy['needs'] == 'build'
raise 'deployment must exclude PRs' unless deploy['if'].include?("github.event_name != 'pull_request'") && deploy['if'].include?('default_branch')
raise 'staging deployment must serialize' unless deploy['environment'] == 'stage' && deploy['concurrency']['cancel-in-progress'] == false
artifact = deploy['steps'].find { |s| s.fetch('uses', '').start_with?('actions/download-artifact@') }
raise 'reruns must download the original build artifact ID' unless artifact['with']['artifact-ids'] == '${{ needs.build.outputs.artifact_id }}'
raise 'no npm scripts with deployment credentials' if deploy['steps'].any? { |s| s.fetch('run', '').include?('npm ') }
w['jobs'].each_value do |job|
 job['steps'].each do |step|
  raise 'actions must be SHA pinned' if step['uses'] && step['uses'].split('@').last !~ /\A[0-9a-f]{40}\z/
 end
end
puts 'PASS: pinned workflow, isolated credential-free builds, default-branch deploy, serialized releases and retry-safe artifact transfer'
