module Commands.FetchTemplates exposing (fetchTemplates, Msg)

import Http
import Models.Resources.Template exposing (Template)
import Commands.Fetch exposing (apiBaseUrl)
import Json.Decode as Decode exposing (field)

type Msg = FetchTemplates (Result Http.Error (List Template))

fetchUrl = String.concat [ apiBaseUrl, "templates" ]

fetchTemplates : Cmd Msg
fetchTemplates =
  Http.get fetchUrl templatesDecoder
    |> Http.send FetchTemplates

templatesDecoder : Decode.Decoder (List Template)
templatesDecoder =
  Decode.list templateDecoder

templateDecoder =
  Decode.map4 Template
    (field "id" Decode.string)
    (field "description" Decode.string)
    (field "version" Decode.string)
    (field "parameters" (Decode.list Decode.string))