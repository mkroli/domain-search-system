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

window.DSSController = ($rootScope, $scope, $resource) ->
  dss = $resource '/api/:ctx/:domain', {},
    getIndex: (method: "GET", params: (ctx: 'index', domain: ''), isArray: true)
    deleteHost: (method: "DELETE", params: (ctx: 'host'))
    saveHost: (method: "PUT", params: (ctx: 'host'))
    searchHost: (method: "GET", params: (ctx: 'host'))

  dss.getIndex (data) ->
    d.state = true for d in data
    $scope.index = data

  $scope.saveHost = (d) ->
    d.state = false
    dss.saveHost((domain: d.id), d.text, ->
      $scope.index = $scope.index
        .filter((o) -> o.id != d.id)
        .concat((id: d.id, text: d.text, state: true))
      d.state = true
      d.id = ''
      d.text = ''
    , -> d.state = true)

  $scope.deleteHost = (d) ->
    d.state = false
    dss.deleteHost((domain: d.id), ->
      $scope.index = $scope.index
        .filter((o) -> o.id != d.id)
    , -> d.state = true)

  $scope.query = ''
  lastQuery = $scope.query
  setInterval(->
    if $scope.query == ''
      $rootScope.$apply -> d.searchResult = false for d in $scope.index
    else if $scope.query != lastQuery
      dss.searchHost((domain: $scope.query), (r) ->
        d.searchResult = d.id == r.id for d in $scope.index
      , ->
        d.searchResult = false for d in $scope.index)
    lastQuery = $scope.query
  , 500)
