###
Copyright 2013 Michael Krolikowski

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
###

angular.module('dss', ['ngResource'])

window.DSSController = ($scope, $resource) ->
  dss = $resource '/api/:domain', {},
    get: (method: "GET", params: (domain: ''), isArray: true)
    delete: (method: "DELETE")
    save: (method: "PUT")

  dss.get (data) ->
    d.state = true for d in data
    $scope.index = data

  $scope.save = (d) ->
    d.state = false
    dss.save((domain: d.id), d.text, ->
      $scope.index = $scope.index
        .filter((o) -> o.id != d.id)
        .concat((id: d.id, text: d.text, state: true))
      d.state = true
      d.id = ''
      d.text = ''
    , -> d.state = true)

  $scope.delete = (d) ->
    d.state = false
    dss.delete((domain: d.id), ->
      $scope.index = $scope.index
        .filter((o) -> o.id != d.id)
    , -> d.state = true)
